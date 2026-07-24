package com.omnijarvis.workflow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.*

class WorkflowScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleDailyCheck(hour: Int = 2, minute: Int = 0) {
        val intent = Intent(context, WorkflowTriggerReceiver::class.java).apply {
            action = "com.omnijarvis.workflow.DAILY_CHECK"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun scheduleWeeklyRelease(dayOfWeek: Int = Calendar.SUNDAY, hour: Int = 3) {
        val intent = Intent(context, WorkflowTriggerReceiver::class.java).apply {
            action = "com.omnijarvis.workflow.WEEKLY_RELEASE"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 1, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)

            if (before(Calendar.getInstance())) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY * 7,
            pendingIntent
        )
    }

    fun cancelAll() {
        listOf(0, 1, 2).forEach { requestCode ->
            val intent = Intent(context, WorkflowTriggerReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}

class WorkflowTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workflow = AutoReleaseWorkflow(context)

        when (intent.action) {
            "com.omnijarvis.workflow.DAILY_CHECK" -> {
                // Check if release needed, only if changes exist
                workflow.triggerAutoRelease()
            }
            "com.omnijarvis.workflow.WEEKLY_RELEASE" -> {
                // Force weekly release
                workflow.triggerManualRelease(AutoReleaseWorkflow.BumpType.MINOR)
            }
            "com.omnijarvis.workflow.IMMEDIATE" -> {
                // User triggered
                workflow.triggerAutoRelease()
            }
        }
    }
}
