package com.example.document.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.document.model.DocumentRecord
import com.example.document.model.WorkflowSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReviewReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getInt(KEY_PENDING, 0)
        if (pending <= 0) return Result.success()

        val oldest = prefs.getLong(KEY_OLDEST, 0L)
        val morning = prefs.getInt(KEY_MORNING, 8)
        val afternoon = prefs.getInt(KEY_AFTERNOON, 15)
        val escalationHours = prefs.getInt(KEY_ESCALATION, 36)
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val escalated = oldest > 0L && now - oldest >= escalationHours * 60L * 60L * 1000L
        if (hour == morning || hour == afternoon || escalated) {
            notifyPending(pending, escalated)
        }
        return Result.success()
    }

    private fun notifyPending(count: Int, escalated: Boolean) {
        createChannel(applicationContext)
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1701,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (escalated) "Revisión atrasada" else "Firmas pendientes"
        val body = if (count == 1) {
            "Tienes 1 plano pendiente de revisión y firma."
        } else {
            "Tienes $count planos pendientes de revisión y firma."
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (escalated) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val PREFS = "skm_review_reminders"
        private const val KEY_PENDING = "pending"
        private const val KEY_OLDEST = "oldest"
        private const val KEY_MORNING = "morning"
        private const val KEY_AFTERNOON = "afternoon"
        private const val KEY_ESCALATION = "escalation"
        private const val CHANNEL_ID = "skm_pending_reviews"
        private const val NOTIFICATION_ID = 1701
        private const val UNIQUE_WORK = "skm-review-reminder-hourly"

        fun schedule(context: Context) {
            createChannel(context)
            val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.HOURS).build()
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
            settings: WorkflowSettings
        ) {
            val pending = documents.filter { it.canBeSignedBy(email) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_PENDING, pending.size)
                .putLong(KEY_OLDEST, pending.minOfOrNull { it.uploadedAt } ?: 0L)
                .putInt(KEY_MORNING, settings.morningHour)
                .putInt(KEY_AFTERNOON, settings.afternoonHour)
                .putInt(KEY_ESCALATION, settings.escalationAfterHours)
                .apply()
            schedule(context)
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
                    description = "Recordatorios de documentos pendientes de revisión y firma"
                }
            )
        }
    }
}
