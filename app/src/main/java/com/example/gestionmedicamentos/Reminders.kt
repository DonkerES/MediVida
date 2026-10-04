package com.example.gestionmedicamentos

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.gestionmedicamentos.data.*
import java.time.LocalDateTime
import java.util.concurrent.Executors

    /** One persisted alarm per occurrence, with a daily refresh; no running Activity is needed. */
@SuppressLint("ApplySharedPref") // Called on IO/executor; durable writes must complete before receiver exits.
class Reminders private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: Reminders? = null
        fun get(context: Context): Reminders = instance ?: synchronized(this) { instance ?: Reminders(context.applicationContext).also { instance = it } }
    }
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    private fun pending(key: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("REMIND").setData(Uri.parse("medivida://alarm/$key")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun cancel() {
        prefs.getStringSet("keys", emptySet())!!.forEach { alarms.cancel(pending(it)) }
        prefs.edit().clear().commit()
        NotificationManagerCompat.from(context).cancelAll()
    }
    private fun schedule(key: String, whenMillis: Long) {
        val keys = prefs.getStringSet("keys", emptySet())!!.toMutableSet().apply { add(key) }
        prefs.edit().putStringSet("keys", keys).commit()
        try {
            if (exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending(key))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending(key))
        } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending(key)) }
    }
    @Synchronized fun rebuild(store: HealthRepository) {
        // Preserve valid snoozes through process recreation and boot.
        val snoozes = prefs.all.filterKeys { it.startsWith("snooze:") }.mapValues { it.value as Long }
        val previous = prefs.getStringSet("keys", emptySet())!!.toSet()
        prefs.getStringSet("keys", emptySet())!!.forEach { alarms.cancel(pending(it)) }
        prefs.edit().clear().commit()
        val owner = store.user() ?: return
        val data = store.snapshot()
        if (!data.profile.notifications) { NotificationManagerCompat.from(context).cancelAll(); return }
        channel()
        val now = LocalDateTime.now()
        for (day in 0L..8L) {
            Schedule.onDate(data.records, now.toLocalDate().plusDays(day)).forEach { task ->
                if ((day <= 1 || task.record.kind == Kind.APPOINTMENT) && data.completions.none { it.recordId == task.record.id && it.occurrence == task.key }) {
                    val prefix = "$owner/${task.record.id}/${task.key}"
                    val deferred = snoozes["snooze:$prefix"]
                    val timestamp = deferred ?: task.epoch
                    if (timestamp > System.currentTimeMillis()) {
                        if (deferred != null) prefs.edit().putLong("snooze:$prefix", deferred).commit()
                        schedule("$prefix/due", timestamp)
                    }
                    if (task.record.kind == Kind.APPOINTMENT) {
                        val offsets = listOf(task.record.reminderMinutes) + if (task.record.dayBefore) listOf(1440) else emptyList()
                        offsets.distinct().filter { it > 0 }.forEach { minutes ->
                            val at = task.epoch - minutes * 60_000L
                            if (at > System.currentTimeMillis()) schedule("$prefix/pre$minutes", at)
                        }
                    }
                }
            }
        }
        // A snooze made at 23:58 still exists after midnight or a reboot.
        snoozes.forEach { (stored, at) ->
            val key = stored.removePrefix("snooze:")
            val parts = key.split('/')
            if (parts.size == 3 && parts[0] == owner && at > System.currentTimeMillis()) {
                val r = data.records.firstOrNull { it.id == parts[1] && !it.archived }
                val time = runCatching { LocalDateTime.parse(parts[2]) }.getOrNull()
                if (r != null && time != null && Schedule.onDate(listOf(r), time.toLocalDate()).any { it.key == parts[2] } && data.completions.none { it.recordId == r.id && it.occurrence == parts[2] }) {
                    prefs.edit().putLong(stored, at).commit(); schedule("$key/due", at)
                }
            }
        }
        previous.filter { key ->
            val parts = key.split('/')
            parts.size == 4 && (data.records.none { it.id == parts[1] && !it.archived } || data.completions.any { it.recordId == parts[1] && it.occurrence == parts[2] })
        }.forEach { NotificationManagerCompat.from(context).cancel(it.substringBeforeLast('/'), 1) }
        schedule("$owner/refresh", now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    @Synchronized fun snooze(store: HealthRepository, record: String, occurrence: String) {
        val uid = store.user() ?: return
        val data = store.snapshot()
        require(data.records.any { it.id == record } && data.completions.none { it.recordId == record && it.occurrence == occurrence })
        val key = "$uid/$record/$occurrence"
        val at = System.currentTimeMillis() + 600_000
        prefs.edit().putLong("snooze:$key", at).commit()
        schedule("$key/due", at)
        NotificationManagerCompat.from(context).cancel(key, 1)
    }
    @Synchronized fun dismissOccurrence(uid: String, record: String, occurrence: String) {
        val prefix = "$uid/$record/$occurrence"
        val keys = prefs.getStringSet("keys", emptySet())!!.toMutableSet()
        keys.filter { it == "$prefix/due" || it.startsWith("$prefix/pre") }.forEach { alarms.cancel(pending(it)); keys.remove(it) }
        prefs.edit().putStringSet("keys", keys).remove("snooze:$prefix").commit()
        NotificationManagerCompat.from(context).cancel(prefix, 1)
    }
    private fun channel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel("health_alarm", "Alarmas de MediVida", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarmas para medicamentos, comidas y citas de MediVida"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                vibrationPattern = longArrayOf(0, 900, 400, 900)
                enableVibration(true); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
    }
    fun deliver(store: HealthRepository, segments: List<String>) {
        if (segments.size < 2 || segments[0] != store.user()) return
        if (segments[1] == "refresh") { rebuild(store); return }
        if (segments.size != 4) return
        val data = store.snapshot()
        if (!data.profile.notifications) return
        val r = data.records.firstOrNull { it.id == segments[1] && !it.archived } ?: return
        val occurrence = segments[2]
        val task = Schedule.onDate(listOf(r), LocalDateTime.parse(occurrence).toLocalDate()).firstOrNull { it.key == occurrence } ?: return
        if (data.completions.any { it.recordId == r.id && it.occurrence == occurrence }) return
        channel()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val key = "${segments[0]}/${r.id}/$occurrence"
        fun activity(action: String): PendingIntent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setAction(action).setData(Uri.parse("medivida://task/$key/$action"))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dueAlarm = !segments[3].startsWith("pre")
        val title = if (dueAlarm) "Es hora de ${r.title}" else "Próxima cita: ${r.title}"
        val alarmUri = Uri.parse("medivida://alarm/$key")
        val openAlarm = if (dueAlarm) PendingIntent.getActivity(context, 0,
            Intent(context, AlarmActivity::class.java).setData(alarmUri)
                .putExtra(AlarmActivity.EXTRA_TITLE, r.title).putExtra(AlarmActivity.EXTRA_DETAIL, r.detail)
                .putExtra(AlarmActivity.EXTRA_OCCURRENCE, occurrence).putExtra(AlarmActivity.EXTRA_KIND, r.kind.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        else activity("open")
        fun alarmAction(action: String, label: String): NotificationCompat.Action {
            val pending = PendingIntent.getBroadcast(context, 0,
                Intent(context, ReminderReceiver::class.java).setAction(action).setData(alarmUri),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Action.Builder(0, label, pending).build()
        }
        val builder = NotificationCompat.Builder(context, "health_alarm").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("${task.dateTime.toLocalTime()} · ${r.detail}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${task.dateTime.toLocalDate()} ${task.dateTime.toLocalTime()} · ${r.detail}\n${r.place}"))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(false).setOngoing(dueAlarm)
            .setCategory(if (dueAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (dueAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setFullScreenIntent(openAlarm, dueAlarm).setContentIntent(openAlarm)
        // Confirming opens the protected alarm screen; snoozing stays available directly.
        if (r.kind == Kind.MEDICINE && !segments[3].startsWith("pre")) {
            builder.addAction(NotificationCompat.Action.Builder(0, "Confirmar toma", openAlarm).build())
            builder.addAction(alarmAction(ReminderReceiver.ACTION_SNOOZE, "En 10 min"))
        }
        val notification = builder.build().apply {
            if (dueAlarm) flags = flags or Notification.FLAG_INSISTENT
        }
        NotificationManagerCompat.from(context).notify(key, 1, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        executor.execute {
            try {
                val store: HealthRepository = if (LocalStore.configured(context)) LocalStore.get(context) else DemoStore.get(context)
                val reminders = Reminders.get(context)
                when (intent.action) {
                    "REMIND" -> reminders.deliver(store, intent.data?.pathSegments.orEmpty())
                    ACTION_TAKE, ACTION_SNOOZE -> handleAlarmAction(store, reminders, intent)
                    else -> reminders.rebuild(store)
                }
            } catch (_: Exception) {
                // Next app resume rebuilds the schedule if the cache is temporarily unavailable.
            } finally { result.finish() }
        }
    }

    private fun handleAlarmAction(store: HealthRepository, reminders: Reminders, intent: Intent) {
        val uri = intent.data ?: return
        val parts = uri.pathSegments
        if (uri.host != "alarm" || parts.size != 3 || parts[0] != store.user()) return
        val (_, recordId, occurrence) = parts
        val record = store.snapshot().records.firstOrNull { it.id == recordId && !it.archived } ?: return
        val dateTime = runCatching { java.time.LocalDateTime.parse(occurrence) }.getOrNull() ?: return
        if (Schedule.onDate(listOf(record), dateTime.toLocalDate()).none { it.key == occurrence }) return
        when (intent.action) {
            ACTION_TAKE -> {
                if (dateTime.isAfter(java.time.LocalDateTime.now())) return
                store.complete(recordId, occurrence, Status.TAKEN)
                reminders.dismissOccurrence(parts[0], recordId, occurrence)
                reminders.rebuild(store)
            }
            ACTION_SNOOZE -> reminders.snooze(store, recordId, occurrence)
        }
    }

    companion object {
        const val ACTION_TAKE = "com.example.gestionmedicamentos.ALARM_TAKE"
        const val ACTION_SNOOZE = "com.example.gestionmedicamentos.ALARM_SNOOZE"
        private val executor = Executors.newSingleThreadExecutor()
    }
}
