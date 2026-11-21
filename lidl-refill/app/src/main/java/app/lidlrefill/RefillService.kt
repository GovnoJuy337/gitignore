package app.lidlrefill

import android.app.*
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.*

class RefillService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            check(this@RefillService)
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("main", "Refill", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "main")
            .setContentTitle("Lidl Refill работает")
            .setContentText("Слежу за остатком мобильного интернета")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        startForeground(1, n)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        /** Мобильный трафик (МБ) с момента since. */
        fun usedMb(ctx: Context, since: Long): Long {
            val nsm = ctx.getSystemService(NetworkStatsManager::class.java)
            val b = nsm.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_CELLULAR, null, since, System.currentTimeMillis()
            )
            return (b.rxBytes + b.txBytes) / (1024 * 1024)
        }

        fun remainingMb(ctx: Context): Long {
            val p = Prefs(ctx)
            return p.baselineRemainingMb - usedMb(ctx, p.baselineTime)
        }

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, RefillService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, RefillService::class.java))

        fun check(ctx: Context) {
            val p = Prefs(ctx)
            if (!p.enabled || p.number.isEmpty() || p.baselineTime == 0L) return
            val rem = try { remainingMb(ctx) } catch (e: Exception) {
                addLog(p, "Ошибка чтения трафика: ${e.message}"); return
            }
            if (rem > p.thresholdMb) return

            val now = System.currentTimeMillis()
            if (now - p.lastSentTime < 3 * 60_000) return // пауза между SMS
            val day = now / 86_400_000
            if (p.dayKey != day) { p.dayKey = day; p.sentToday = 0 }
            if (p.sentToday >= p.maxPerDay) {
                addLog(p, "Лимит ${p.maxPerDay} Refill в сутки достигнут, SMS не отправлено")
                p.lastSentTime = now
                return
            }
            try {
                SmsManager.getDefault().sendTextMessage(p.number, null, p.text, null, null)
            } catch (e: Exception) {
                addLog(p, "Не удалось отправить SMS: ${e.message}"); return
            }
            p.sentToday += 1
            p.lastSentTime = now
            // Считаем, что Refill сработал: начинаем отсчёт заново.
            p.baselineRemainingMb = rem + p.packMb
            p.baselineTime = now
            addLog(p, "Отправлено «${p.text}» на ${p.number}. Осталось было ~$rem МБ")
        }

        fun addLog(p: Prefs, msg: String) {
            val t = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
            p.log = (listOf("$t  $msg") + p.log.lines().filter { it.isNotBlank() }).take(30).joinToString("\n")
        }
    }
}
