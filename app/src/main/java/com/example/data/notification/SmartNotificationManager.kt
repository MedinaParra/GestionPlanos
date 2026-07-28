package com.example.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

object SmartNotificationManager {

    private const val CHANNEL_ID = "skm_industrial_ot_channel"
    private const val CHANNEL_NAME = "SKM Industrial - Avisos de Firma OT"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de firma de planos y retrasos SKM Industrial"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    enum class ScheduleWindow {
        NORMAL_WORK_HOURS,    // 08:00 - 16:00
        EXTENDED_DELAY_HOURS, // 16:00 - 19:00
        OFF_HOURS,            // 19:00 - 08:00
        CRITICAL_HOURLY_PING  // Multi-day overdue (24/7 every hour)
    }

    fun getCurrentWindow(daysPending: Int = 0): ScheduleWindow {
        if (daysPending >= 2) {
            return ScheduleWindow.CRITICAL_HOURLY_PING
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 8..15 -> ScheduleWindow.NORMAL_WORK_HOURS
            in 16..18 -> ScheduleWindow.EXTENDED_DELAY_HOURS
            else -> ScheduleWindow.OFF_HOURS
        }
    }

    fun shouldDeliverNotification(daysPending: Int): Boolean {
        val window = getCurrentWindow(daysPending)
        return when (window) {
            ScheduleWindow.NORMAL_WORK_HOURS -> true
            ScheduleWindow.EXTENDED_DELAY_HOURS -> true
            ScheduleWindow.CRITICAL_HOURLY_PING -> true
            ScheduleWindow.OFF_HOURS -> false
        }
    }

    fun sendBlueprintEmittedNotification(
        context: Context,
        otId: String,
        blueprintName: String,
        daysPending: Int = 0,
        pendingApproversCount: Int = 6
    ): Boolean {
        createNotificationChannel(context)

        val window = getCurrentWindow(daysPending)
        val title: String
        val body: String

        when (window) {
            ScheduleWindow.NORMAL_WORK_HOURS -> {
                title = "📋 SKM Industrial: Nuevo Plano Emitido ($otId)"
                body = "El plano '$blueprintName' requiere revisión. Quedan $pendingApproversCount revisores pendientes. (Horario 08:00 - 16:00)"
            }
            ScheduleWindow.EXTENDED_DELAY_HOURS -> {
                title = "⏳ SKM Industrial: Retraso en Firma ($otId)"
                body = "Atención: Plano '$blueprintName' con retraso de firma. Horario extendido de avisos hasta las 19:00h."
            }
            ScheduleWindow.CRITICAL_HOURLY_PING -> {
                title = "🚨 ALERTA CRÍTICA CADA HORA: Plano $otId Sin Firmar"
                body = "URGENTE SKM: Han pasado $daysPending días sin completar las 6 firmas del plano '$blueprintName'. ¡Por favor firmar ahora!"
            }
            ScheduleWindow.OFF_HOURS -> {
                // Return false because notifications are suppressed outside work hours unless critical
                return false
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify((otId.hashCode() + System.currentTimeMillis() % 1000).toInt(), builder.build())
        return true
    }
}
