const express = require("express");
const cors = require("cors");
const multer = require("multer");
const path = require("path");
const fs = require("fs");
const os = require("os");
const crypto = require("crypto");
const dns = require("dns").promises;
const net = require("net");
const { spawn } = require("child_process");

const app = express();
const PORT = Number(process.env.PORT) || 10000;

const UPLOAD_DIR = process.env.NEXUS_UPLOAD_DIR || path.join(os.tmpdir(), "nexus-uploads");
const CLIPS_DIR = process.env.NEXUS_CLIPS_DIR || path.join(os.tmpdir(), "nexus-clips");

fs.mkdirSync(UPLOAD_DIR, { recursive: true });
fs.mkdirSync(CLIPS_DIR, { recursive: true });

app.use(cors());
app.use(express.json({ limit: "2mb" }));
app.use(express.static(path.join(__dirname, "public")));
app.use(
  "/clips",
  express.static(CLIPS_DIR, {
    setHeaders(res, filePath) {
      res.setHeader("Content-Type", "video/mp4");
      res.setHeader(
        "Content-Disposition",
        `inline; filename="${path.basename(filePath)}"`
      );
    },
  })
);

const upload = multer({
  dest: UPLOAD_DIR,
  limits: {
    fileSize: 120 * 1024 * 1024, // 120 MB max upload
  },
});

function isPrivateIpAddress(ip) {
  if (net.isIPv4(ip)) {
    const parts = ip.split(".").map(Number);
    if (parts[0] === 10 || parts[0] === 127 || parts[0] === 0) return true;
    if (parts[0] === 169 && parts[1] === 254) return true;
    if (parts[0] === 192 && parts[1] === 168) return true;
    if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) return true;
    return false;
  }
  if (net.isIPv6(ip)) {
    const lower = ip.toLowerCase();
    return (
      lower === "::1" ||
      lower === "::" ||
      lower.startsWith("fc") ||
      lower.startsWith("fd") ||
      lower.startsWith("fe80") ||
      lower.startsWith("::ffff:127.") ||
      lower.startsWith("::ffff:10.") ||
      lower.startsWith("::ffff:192.168.")
    );
  }
  return true;
}

async function validateExternalVideoUrl(rawUrl) {
  let parsed;
  try {
    parsed = new URL(String(rawUrl || "").trim());
  } catch {
    throw new Error("Invalid video URL format.");
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error("Only http:// and https:// URLs are supported.");
  }
  const host = parsed.hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".local") || host.endsWith(".internal")) {
    throw new Error("Local or internal hostnames are not permitted.");
  }
  const records = await dns.lookup(host, { all: true });
  if (!records || records.length === 0) {
    throw new Error("Could not resolve video URL hostname.");
  }
  for (const rec of records) {
    if (isPrivateIpAddress(rec.address)) {
      throw new Error("Private or loopback IP targets are blocked.");
    }
  }
  return parsed.toString();
}

function runProcess(bin, args, timeoutMs = 140000) {
  return new Promise((resolve, reject) => {
    const child = spawn(bin, args, { stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    const timer = setTimeout(() => {
      child.kill("SIGKILL");
      reject(new Error(`${bin} timed out after ${Math.round(timeoutMs / 1000)}s.`));
    }, timeoutMs);

    child.stdout.on("data", (chunk) => {
      if (stdout.length < 16384) stdout += chunk.toString();
    });
    child.stderr.on("data", (chunk) => {
      if (stderr.length < 24576) stderr += chunk.toString();
    });
    child.on("error", (err) => {
      clearTimeout(timer);
      reject(err);
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      if (code === 0) {
        resolve({ stdout, stderr });
      } else {
        const tail = stderr.trim().split("\n").slice(-4).join(" | ");
        reject(new Error(`${bin} exited with code ${code}: ${tail || "processing error"}`));
      }
    });
  });
}

async function runFfmpegClip({ inputPathOrUrl, outputPath, startSec, durationSec, cropVertical }) {
  const safeStart = Math.max(0, Number(startSec) || 0);
  const safeDuration = Math.min(60, Math.max(3, Number(durationSec) || 15));
  const vfFilter = cropVertical
    ? "crop='min(iw,ih*9/16)':'min(ih,iw*16/9)',scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280,setsar=1"
    : "scale='min(1280,iw)':-2,setsar=1";

  const baseArgs = [
    "-hide_banner",
    "-loglevel",
    "error",
    "-y",
    "-ss",
    String(safeStart),
    "-i",
    inputPathOrUrl,
    "-t",
    String(safeDuration),
    "-vf",
    vfFilter,
    "-c:v",
    "libx264",
    "-preset",
    "veryfast",
    "-pix_fmt",
    "yuv420p",
    "-crf",
    "23",
  ];

  try {
    await runProcess("ffmpeg", [
      ...baseArgs,
      "-c:a",
      "aac",
      "-b:a",
      "128k",
      "-movflags",
      "+faststart",
      outputPath,
    ]);
  } catch (firstErr) {
    // Retry without audio codec if input clip has no audio stream
    await runProcess("ffmpeg", [
      ...baseArgs,
      "-an",
      "-movflags",
      "+faststart",
      outputPath,
    ]).catch(() => {
      throw firstErr;
    });
  }
}

function isDirectStreamUrl(urlStr) {
  const clean = urlStr.toLowerCase().split("?")[0];
  return (
    clean.endsWith(".mp4") ||
    clean.endsWith(".mov") ||
    clean.endsWith(".m4v") ||
    clean.endsWith(".webm") ||
    clean.endsWith(".m3u8")
  );
}

app.get("/api/health", async (_req, res) => {
  let ffmpegLine = "unavailable";
  let ytdlpLine = "unavailable";
  try {
    const ff = await runProcess("ffmpeg", ["-version"], 8000);
    ffmpegLine = (ff.stdout.split("\n")[0] || "FFmpeg Ready").slice(0, 48);
  } catch {
    ffmpegLine = "ffmpeg missing";
  }
  try {
    const yt = await runProcess("yt-dlp", ["--version"], 8000);
    ytdlpLine = yt.stdout.trim().slice(0, 24) || "ready";
  } catch {
    ytdlpLine = "yt-dlp optional";
  }

  res.json({
    status: "ok",
    service: "Nexus FFmpeg + yt-dlp Cloud Video Pipeline",
    owner: "Arnold Chirchir (@arnold_60011 | youtube.com/@arnold60011)",
    ffmpeg: ffmpegLine,
    ytdlp: ytdlpLine,
  });
});

app.post("/api/clip-url", async (req, res) => {
  let tempSourceFile = null;
  const clipId = `nexus_${crypto.randomBytes(6).toString("hex")}`;
  const outputPath = path.join(CLIPS_DIR, `${clipId}.mp4`);

  try {
    const safeUrl = await validateExternalVideoUrl(req.body?.url);
    const startSec = Math.max(0, Number(req.body?.startSec) || 0);
    const durationSec = Math.min(60, Math.max(3, Number(req.body?.durationSec) || 15));
    const cropVertical = req.body?.cropVertical !== false;

    if (isDirectStreamUrl(safeUrl)) {
      try {
        await runFfmpegClip({
          inputPathOrUrl: safeUrl,
          outputPath,
          startSec,
          durationSec,
          cropVertical,
        });
      } catch {
        tempSourceFile = path.join(UPLOAD_DIR, `dl_${clipId}.mp4`);
        await runProcess("yt-dlp", [
          "--no-playlist",
          "--max-filesize",
          "120M",
          "-f",
          "mp4[height<=1080]/best[ext=mp4]/best",
          "-o",
          tempSourceFile,
          "--",
          safeUrl,
        ]);
        await runFfmpegClip({
          inputPathOrUrl: tempSourceFile,
          outputPath,
          startSec,
          durationSec,
          cropVertical,
        });
      }
    } else {
      tempSourceFile = path.join(UPLOAD_DIR, `dl_${clipId}.mp4`);
      await runProcess("yt-dlp", [
        "--no-playlist",
        "--max-filesize",
        "120M",
        "-f",
        "mp4[height<=1080]/best[ext=mp4]/best",
        "-o",
        tempSourceFile,
        "--",
        safeUrl,
      ]);
      await runFfmpegClip({
        inputPathOrUrl: tempSourceFile,
        outputPath,
        startSec,
        durationSec,
        cropVertical,
      });
    }

    const stat = fs.statSync(outputPath);
    if (!stat || stat.size < 512) {
      throw new Error("FFmpeg produced an empty clip output.");
    }

    res.json({
      ok: true,
      clipId,
      clipUrl: `/clips/${clipId}.mp4`,
      downloadUrl: `/clips/${clipId}.mp4`,
      durationSec,
      sizeBytes: stat.size,
      pipeline: "render-ffmpeg-url",
    });
  } catch (err) {
    try {
      if (fs.existsSync(outputPath)) fs.unlinkSync(outputPath);
    } catch {}
    res.status(400).json({
      ok: false,
      error: err.message || "Failed to clip external URL via FFmpeg.",
    });
  } finally {
    if (tempSourceFile) {
      try {
        if (fs.existsSync(tempSourceFile)) fs.unlinkSync(tempSourceFile);
      } catch {}
    }
  }
});

app.post("/api/clip-upload", upload.single("video"), async (req, res) => {
  const uploadedFile = req.file?.path;
  if (!uploadedFile) {
    return res.status(400).json({ ok: false, error: "Missing 'video' file in multipart upload." });
  }

  const clipId = `nexus_${crypto.randomBytes(6).toString("hex")}`;
  const outputPath = path.join(CLIPS_DIR, `${clipId}.mp4`);

  try {
    const startSec = Math.max(0, Number(req.body?.startSec) || 0);
    const durationSec = Math.min(60, Math.max(3, Number(req.body?.durationSec) || 15));
    const cropVertical = String(req.body?.cropVertical ?? "true") !== "false";

    await runFfmpegClip({
      inputPathOrUrl: uploadedFile,
      outputPath,
      startSec,
      durationSec,
      cropVertical,
    });

    const stat = fs.statSync(outputPath);
    if (!stat || stat.size < 512) {
      throw new Error("FFmpeg produced an empty clip from uploaded file.");
    }

    return res.json({
      ok: true,
      clipId,
      clipUrl: `/clips/${clipId}.mp4`,
      downloadUrl: `/clips/${clipId}.mp4`,
      durationSec,
      sizeBytes: stat.size,
      pipeline: "render-ffmpeg-upload",
    });
  } catch (err) {
    try {
      if (fs.existsSync(outputPath)) fs.unlinkSync(outputPath);
    } catch {}
    return res.status(400).json({
      ok: false,
      error: err.message || "Failed to clip uploaded video via FFmpeg.",
    });
  } finally {
    try {
      if (fs.existsSync(uploadedFile)) fs.unlinkSync(uploadedFile);
    } catch {}
  }
});

// Periodic cleanup of temporary rendered clips older than 60 minutes
setInterval(() => {
  const cutoff = Date.now() - 60 * 60 * 1000;
  for (const dir of [CLIPS_DIR, UPLOAD_DIR]) {
    try {
      for (const name of fs.readdirSync(dir)) {
        const full = path.join(dir, name);
        const st = fs.statSync(full);
        if (st.mtimeMs < cutoff) {
          fs.unlinkSync(full);
        }
      }
    } catch {}
  }
}, 15 * 60 * 1000).unref();

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Nexus Render FFmpeg Pipeline listening on 0.0.0.0:${PORT}`);
});
