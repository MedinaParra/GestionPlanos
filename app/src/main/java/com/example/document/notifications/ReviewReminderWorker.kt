package com.example.document.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.document.drive.DriveRestClient
import com.example.document.model.DocumentRecord
import com.example.document.model.DriveConfiguration
import com.example.document.model.WorkflowSettings
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReviewReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousIds = prefs.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty()
        val refreshed = refreshPendingFromDrive(prefs)
        val snapshot = refreshed ?: PendingSnapshot(
            count = prefs.getInt(KEY_PENDING, 0),
            oldest = prefs.getLong(KEY_OLDEST, 0L),
            ids = previousIds
        )

        if (refreshed != null) {
            saveSnapshot(prefs, refreshed)
            val newlyAssigned = refreshed.ids - previousIds
            if (newlyAssigned.isNotEmpty()) {
                postPendingNotification(applicationContext, refreshed.count, escalated = false, newAssignment = true)
            }
        }

        if (snapshot.count <= 0) return Result.success()

        val morning = prefs.getInt(KEY_MORNING, 8)
        val afternoon = prefs.getInt(KEY_AFTERNOON, 15)
        val escalationHours = prefs.getInt(KEY_ESCALATION, 36)
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val escalated = snapshot.oldest > 0L &&
            now - snapshot.oldest >= escalationHours * 60L * 60L * 1000L
        val shouldNotify = hour == morning || hour == afternoon || escalated
        val hourBucket = now / (60L * 60L * 1000L)
        val lastBucket = prefs.getLong(KEY_LAST_NOTIFICATION_BUCKET, -1L)

        if (shouldNotify && lastBucket != hourBucket) {
            postPendingNotification(applicationContext, snapshot.count, escalated, newAssignment = false)
            prefs.edit().putLong(KEY_LAST_NOTIFICATION_BUCKET, hourBucket).apply()
        }
        return Result.success()
    }

    private suspend fun refreshPendingFromDrive(prefs: SharedPreferences): PendingSnapshot? {
        val email = prefs.getString(KEY_EMAIL, "").orEmpty()
        val indexFileId = prefs.getString(KEY_INDEX_FILE_ID, "").orEmpty()
        if (email.isBlank() || indexFileId.isBlank()) return null

        return runCatching {
            val request = AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(Scope(DRIVE_SCOPE), Scope(SHEETS_SCOPE)))
                .filterByHostedDomain("skmindustrial.cl")
                .build()
            val result = Identity.getAuthorizationClient(applicationContext)
                .authorize(request)
                .await()
            if (result.hasResolution()) {
                postReconnectNotification(applicationContext, prefs)
                return null
            }
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                postReconnectNotification(applicationContext, prefs)
                return null
            }

            val root = JSONObject(DriveRestClient().readTextFile(token, indexFileId))
            val documents = root.optJSONArray("documents") ?: JSONArray()
            val ids = linkedSetOf<String>()
            var oldest = 0L
            for (index in 0 until documents.length()) {
                val item = documents.optJSONObject(index) ?: continue
                if (item.optString("status") != "EN_REVISIÓN") continue
                val reviewers = item.optJSONArray("requiredReviewerEmails") ?: JSONArray()
                val reviewerIndex = item.optInt("currentReviewerIndex")
                val currentReviewer = reviewers.optString(reviewerIndex)
                if (!currentReviewer.equals(email, ignoreCase = true)) continue
                ids += item.optString("id")
                val uploadedAt = item.optLong("uploadedAt")
                if (uploadedAt > 0L && (oldest == 0L || uploadedAt < oldest)) oldest = uploadedAt
            }
            PendingSnapshot(ids.size, oldest, ids)
        }.getOrElse {
            null
        }
    }

    companion object {
        private const val PREFS = "skm_review_reminders"
        private const val KEY_PENDING = "pending"
        private const val KEY_OLDEST = "oldest"
        private const val KEY_PENDING_IDS = "pending_ids"
        private const val KEY_EMAIL = "email"
        private const val KEY_INDEX_FILE_ID = "index_file_id"
        private const val KEY_MORNING = "morning"
        private const val KEY_AFTERNOON = "afternoon"
        private const val KEY_ESCALATION = "escalation"
        private const val KEY_LAST_NOTIFICATION_BUCKET = "last_notification_bucket"
        private const val KEY_LAST_RECONNECT_NOTICE = "last_reconnect_notice"
        private const val CHANNEL_ID = "skm_pending_reviews_v2"
        private const val NOTIFICATION_ID = 1701
        private const val RECONNECT_NOTIFICATION_ID = 1702
        private const val UNIQUE_WORK = "skm-review-reminder-hourly-v2"
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"

        fun schedule(context: Context) {
            createChannel(context)
            val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun updateCache(
            context: Context,
            email: String,
            documents: List<DocumentRecord>,
            settings: WorkflowSettings,
            configuration: DriveConfiguration
        ) {
            val pending = documents.filter { it.canBeSignedBy(email) }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previousIds = prefs.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty()
            val currentIds = pending.mapTo(linkedSetOf()) { it.id }

            prefs.edit()
                .putInt(KEY_PENDING, pending.size)
                .putLong(KEY_OLDEST, pending.minOfOrNull { it.uploadedAt } ?: 0L)
                .putStringSet(KEY_PENDING_IDS, currentIds)
                .putString(KEY_EMAIL, email)
                .putString(KEY_INDEX_FILE_ID, configuration.indexFileId)
                .putInt(KEY_MORNING, settings.morningHour)
                .putInt(KEY_AFTERNOON, settings.afternoonHour)
                .putInt(KEY_ESCALATION, settings.escalationAfterHours)
                .apply()

            val newAssignments = currentIds - previousIds
            if (newAssignments.isNotEmpty()) {
                postPendingNotification(context, pending.size, escalated = false, newAssignment = true)
            }
            schedule(context)
        }

        private fun saveSnapshot(prefs: SharedPreferences, snapshot: PendingSnapshot) {
            prefs.edit()
                .putInt(KEY_PENDING, snapshot.count)
                .putLong(KEY_OLDEST, snapshot.oldest)
                .putStringSet(KEY_PENDING_IDS, snapshot.ids)
                .apply()
        }

        private fun postPendingNotification(
            context: Context,
            count: Int,
            escalated: Boolean,
            newAssignment: Boolean
        ) {
            createChannel(context)
            if (
                android.os.Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            val pendingIntent = appPendingIntent(context)
            val title = when {
                newAssignment -> "Nuevo plano asignado para revisión"
                escalated -> "Revisión de plano atrasada"
                else -> "Revisiones de planos pendientes"
            }
            val body = if (count == 1) {
                "Tienes 1 plano pendiente: revisa, comenta, aprueba o solicita cambios."
            } else {
                "Tienes $count planos pendientes: revisa, comenta, aprueba o solicita cambios."
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        private fun postReconnectNotification(context: Context, prefs: SharedPreferences) {
            val now = System.currentTimeMillis()
            val last = prefs.getLong(KEY_LAST_RECONNECT_NOTICE, 0L)
            if (now - last < 12L * 60L * 60L * 1000L) return
            if (
                android.os.Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            createChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Reconecta Google Drive")
                .setContentText("Abre Gestión de Planos para renovar el acceso y continuar recibiendo revisiones.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentIntent(appPendingIntent(context))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(RECONNECT_NOTIFICATION_ID, notification)
            prefs.edit().putLong(KEY_LAST_RECONNECT_NOTICE, now).apply()
        }

        private fun appPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                1701,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun createChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Revisiones de planos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos del sistema para nuevos planos, pendientes y revisiones atrasadas"
                    enableVibration(true)
                    setShowBadge(true)
                }
            )
        }
    }

    private data class PendingSnapshot(
        val count: Int,
        val oldest: Long,
        val ids: Set<String>
    )
}
