package ru.kommunalka.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Раз в сутки проверяет сроки и показывает уведомление. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            Reminders.check(applicationContext, test = false)
        } catch (e: Exception) {
            // Ошибка в одной проверке не должна останавливать ежедневные напоминания
            Log.e("Kommunalka", "Ошибка проверки напоминаний", e)
        }
        return Result.success()
    }
}

object Reminders {
    const val CHANNEL_ID = "reminders"
    private const val WORK_NAME = "daily-reminder"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL_ID, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Передача показаний, сроки оплаты и поверки счётчиков"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Ежедневная проверка в выбранный час. replace = true — перепланировать (если сменили время). */
    fun schedule(ctx: Context, hour: Int, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** О чём стоит напомнить сегодня. Об оплате — только за 5 дней до срока и после. */
    fun pending(state: AppState, today: LocalDate): List<Pair<Apartment, Alert>> =
        state.apts.flatMap { a -> a.alerts(today).map { a to it } }.filter { (_, al) ->
            when {
                al.level == Level.BAD -> true
                al.level == Level.WARN && al.goal == Goal.PAY -> (al.daysLeft ?: 0) <= 5
                al.level == Level.WARN -> true
                else -> false
            }
        }

    /** Показывает уведомление. test = true — показать сразу, даже если сегодня уже напоминали. */
    @SuppressLint("MissingPermission")
    fun check(ctx: Context, test: Boolean): Boolean {
        if (!canNotify(ctx)) return false
        val today = LocalDate.now()
        val items = pending(Repo.get(ctx).state.value, today)
        val prefs = ctx.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        if (!test && (items.isEmpty() || prefs.getString("last", null) == today.toString())) return false

        val multiApt = items.map { it.first.id }.distinct().size > 1
        fun line(p: Pair<Apartment, Alert>) = (if (multiApt) p.first.name + ": " else "") + p.second.title

        val title = when {
            items.isEmpty() -> "Срочных дел нет"
            items.size == 1 -> items[0].second.title
            else -> "Коммуналка: ${items.size} ${plural(items.size, "дело", "дела", "дел")}"
        }
        val text = when {
            items.isEmpty() -> "Напоминания работают — сообщим, когда что-то понадобится"
            items.size == 1 -> listOf(if (multiApt) items[0].first.name else "", items[0].second.sub).filter { it.isNotBlank() }.joinToString(" · ")
            else -> line(items[0])
        }
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (items.size > 1) {
            val style = NotificationCompat.InboxStyle()
            items.take(6).forEach { style.addLine(line(it)) }
            if (items.size > 6) style.setSummaryText("и ещё ${items.size - 6}")
            builder.setStyle(style)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build())
        if (!test) prefs.edit().putString("last", today.toString()).apply()
        return true
    }
}
