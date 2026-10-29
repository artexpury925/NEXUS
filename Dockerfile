FROM node:20-bookworm-slim

# Install FFmpeg, Python3 (required by yt-dlp), curl, and CA certificates
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ffmpeg \
      python3 \
      ca-certificates \
      curl && \
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /srv/nexus

COPY render-backend/package.json ./package.json
RUN npm install --omit=dev --no-audit --no-fund

COPY render-backend/ ./

RUN mkdir -p /tmp/nexus-uploads /tmp/nexus-clips

ENV NODE_ENV=production
ENV PORT=10000

EXPOSE 10000

CMD ["node", "server.js"]
