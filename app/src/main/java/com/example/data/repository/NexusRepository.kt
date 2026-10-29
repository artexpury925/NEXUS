package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.StudioCreation
import com.example.data.model.UserProfile
import com.example.data.model.VideoClipProject
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NexusRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- User Profile ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path, auth)
                throw error
            }
    }

    suspend fun ensureUserProfile(displayName: String, email: String): UserProfile {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                return existing.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid, displayName = displayName, email = email)
            }
            val safeName = displayName.trim().ifBlank { "Nexus Creator" }.take(100)
            val safeEmail = email.trim().ifBlank { "creator@nexus.app" }.take(200)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "email" to safeEmail,
                "creatorSupportVisited" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val created = docRef.get().await()
            return created.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, displayName = safeName, email = safeEmail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    suspend fun markCreatorSupportVisited() {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "creatorSupportVisited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateConnectedYouTubeChannel(channelTitle: String?) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "youtubeChannelTitle" to channelTitle?.take(150),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    // --- Chat & Task Messages ---

    fun observeMessages(userId: String): Flow<List<ChatMessage>> {
        val path = "users/$userId/messages"
        return db.collection("users").document(userId).collection("messages")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path, auth)
                throw error
            }
    }

    suspend fun addMessage(role: String, text: String, mode: String): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("messages")
        try {
            val payload = mapOf(
                "userId" to uid,
                "role" to role,
                "text" to text.take(20000),
                "mode" to mode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val docRef = colRef.add(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path, auth)
            throw e
        }
    }

    suspend fun getMessageById(targetOwnerId: String, messageId: String): ChatMessage? {
        val docRef = db.collection("users").document(targetOwnerId)
            .collection("messages").document(messageId)
        try {
            val snapshot = docRef.get().await()
            return snapshot.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            throw e
        }
    }

    suspend fun clearMessages() {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("messages")
        try {
            val docs = colRef.whereEqualTo("userId", uid).get().await()
            for (doc in docs.documents) {
                doc.reference.delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path, auth)
            throw e
        }
    }

    // --- Studio Creations (Web Apps & Games) ---

    fun observeCreations(userId: String): Flow<List<StudioCreation>> {
        val path = "users/$userId/creations"
        return db.collection("users").document(userId).collection("creations")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(StudioCreation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path, auth)
                throw error
            }
    }

    suspend fun saveCreation(
        title: String,
        prompt: String,
        category: String,
        summary: String,
        htmlCode: String
    ): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("creations")
        val validCategory = if (category in setOf("webapp", "game", "clone", "dropship")) category else "webapp"
        try {
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().ifBlank { "Nexus App" }.take(120),
                "prompt" to prompt.trim().take(4000),
                "category" to validCategory,
                "summary" to summary.trim().ifBlank { "Interactive build" }.take(1000),
                "htmlCode" to htmlCode.take(250000),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            return colRef.add(payload).await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path, auth)
            throw e
        }
    }

    suspend fun updateCreation(
        creationId: String,
        title: String,
        summary: String,
        htmlCode: String
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("creations").document(creationId)
        try {
            docRef.update(
                mapOf(
                    "title" to title.trim().ifBlank { "Rebranded App" }.take(120),
                    "summary" to summary.trim().ifBlank { "Rebranded in Nexus Studio" }.take(1000),
                    "htmlCode" to htmlCode.take(250000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteCreation(creationId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("creations").document(creationId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    // --- Automation Workflows ---

    fun observeWorkflows(userId: String): Flow<List<AutomationWorkflow>> {
        val path = "users/$userId/workflows"
        return db.collection("users").document(userId).collection("workflows")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AutomationWorkflow::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path, auth)
                throw error
            }
    }

    suspend fun saveWorkflow(
        title: String,
        goal: String,
        triggerType: String,
        steps: List<String>
    ): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("workflows")
        val sanitizedSteps = steps.map { it.trim().take(1000) }.filter { it.isNotEmpty() }.take(10)
            .ifEmpty { listOf("Analyze input and generate output") }
        try {
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().ifBlank { "Nexus Workflow" }.take(120),
                "goal" to goal.trim().take(2000),
                "triggerType" to triggerType.trim().ifBlank { "Manual" }.take(60),
                "steps" to sanitizedSteps,
                "runCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            return colRef.add(payload).await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path, auth)
            throw e
        }
    }

    suspend fun recordWorkflowExecution(workflowId: String, output: String, nextRunCount: Long) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("workflows").document(workflowId)
        try {
            docRef.update(
                mapOf(
                    "lastRunOutput" to output.take(20000),
                    "runCount" to nextRunCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteWorkflow(workflowId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("workflows").document(workflowId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    // --- YouTube Video Clips ---

    fun observeClips(userId: String): Flow<List<VideoClipProject>> {
        val path = "users/$userId/clips"
        return db.collection("users").document(userId).collection("clips")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(VideoClipProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path, auth)
                throw error
            }
    }

    suspend fun saveClipProject(
        title: String,
        description: String,
        prompt: String,
        sourceType: String,
        durationSec: Int,
        localFilePath: String,
        tags: List<String>
    ): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("clips")
        val cleanTags = tags.map { it.trim().take(50) }.filter { it.isNotEmpty() }.take(10)
        try {
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().ifBlank { "Nexus Short Clip" }.take(100),
                "description" to description.trim().ifBlank { "Created with Nexus Studio" }.take(5000),
                "prompt" to prompt.trim().ifBlank { "Clip highlight" }.take(2000),
                "sourceType" to if (sourceType == "trimmed") "trimmed" else "generated",
                "durationSec" to durationSec.coerceIn(1, 180).toLong(),
                "localFilePath" to localFilePath.take(500),
                "youtubeStatus" to "ready",
                "tags" to cleanTags,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            return colRef.add(payload).await().id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path, auth)
            throw e
        }
    }

    suspend fun updateClipYouTubeStatus(
        clipId: String,
        status: String,
        youtubeVideoId: String? = null
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("clips").document(clipId)
        try {
            docRef.update(
                mapOf(
                    "youtubeStatus" to status,
                    "youtubeVideoId" to youtubeVideoId?.take(64),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteClip(clipId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("clips").document(clipId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }
}
