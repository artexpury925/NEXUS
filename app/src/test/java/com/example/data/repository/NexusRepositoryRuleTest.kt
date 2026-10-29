package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class NexusRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfileAndSupportCreator_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@nexus.test")
        val repository = NexusRepository(firestore, auth)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile("Alice Creator", "alice@nexus.test")
        }
        assertEquals(uid, profile.userId)
        assertEquals(false, profile.creatorSupportVisited)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.markCreatorSupportVisited()
        }

        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it?.creatorSupportVisited == true }
        }
        assertNotNull(updated)
        assertTrue(updated!!.creatorSupportVisited)
    }

    @Test
    fun createAndObserveCreationsWorkflowsAndClips_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("studio_${UUID.randomUUID()}@nexus.test")
        val repository = NexusRepository(firestore, auth)

        val creationId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveCreation(
                title = "Cyber Pong",
                prompt = "Build a neon pong game",
                category = "game",
                summary = "Touch paddle HTML5 game",
                htmlCode = "<!DOCTYPE html><html><body><h1>Cyber Pong</h1></body></html>"
            )
        }
        assertTrue(creationId.isNotEmpty())

        val creations = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCreations(uid).first { list -> list.any { it.id == creationId } }
        }
        assertTrue(creations.any { it.id == creationId })

        val dropshipId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveCreation(
                title = "Apex Dropship Store",
                prompt = "Smart LED Desk Lamp Storefront",
                category = "dropship",
                summary = "Dropship store + ROAS lab",
                htmlCode = "<!DOCTYPE html><html><body><h1>Apex Store</h1></body></html>"
            )
        }
        assertTrue(dropshipId.isNotEmpty())

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateCreation(
                creationId = dropshipId,
                title = "Vortex Luxury Store",
                summary = "Rebranded luxury dropship storefront",
                htmlCode = "<!DOCTYPE html><html><body><h1>Vortex Luxury Store</h1></body></html>"
            )
        }

        val updatedCreations = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCreations(uid).first { list ->
                list.any { it.id == dropshipId && it.title == "Vortex Luxury Store" }
            }
        }
        assertEquals("dropship", updatedCreations.first { it.id == dropshipId }.category)

        val clipId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveClipProject(
                title = "Nexus AI Highlights",
                description = "Subscribe to @arnold60011 #shorts",
                prompt = "Make a 10s highlight clip",
                sourceType = "generated",
                durationSec = 10,
                localFilePath = "/data/user/0/clips/test.mp4",
                tags = listOf("nexus", "ai")
            )
        }
        assertTrue(clipId.isNotEmpty())

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateClipYouTubeStatus(clipId, "uploaded", "yt_vid_123")
        }

        val clips = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeClips(uid).first { list -> list.any { it.id == clipId && it.youtubeStatus == "uploaded" } }
        }
        assertEquals("yt_vid_123", clips.first { it.id == clipId }.youtubeVideoId)
    }

    @Test
    fun getMessageById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_cross_${UUID.randomUUID()}@nexus.test")
        val aliceRepo = NexusRepository(firestore, auth)
        val messageId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addMessage("user", "Private task note", "task")
        }

        signInTestUser("bob_cross_${UUID.randomUUID()}@nexus.test")
        val bobRepo = NexusRepository(firestore, auth)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getMessageById(aliceUid, messageId)
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for cross-user access")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    @Test
    fun observeMessages_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = NexusRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeMessages("some_user_123").first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
