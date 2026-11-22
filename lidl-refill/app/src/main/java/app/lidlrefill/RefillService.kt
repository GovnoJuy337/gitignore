package app.lidlrefill

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import java.text.SimpleDateFormat
import java.util.*

class RefillService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            check(this@RefillService)
            handler.postDelayed(this, Prefs(this@RefillService).checkSec.coerceAtLeast(5) * 1000L)
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
        /** Счётчик мобильного трафика Android с момента включения телефона (байты, точный, в реальном времени). */
        private fun mobileBytes(): Long = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()

        /** Прибавляет к накопленному расходу то, что потрачено с прошлой проверки. */
        @Synchronized
        fun poll(ctx: Context) {
            val p = Prefs(ctx)
            val cur = mobileBytes()
            if (cur < 0) return
            // Если счётчик уменьшился — телефон перезагружался, считаем с нуля.
            val delta = if (cur >= p.lastCounter) cur - p.lastCounter else cur
            p.usedBytes += delta
            p.lastCounter = cur
        }

        /** Начать отсчёт заново: сейчас осталось remMb. */
        fun resetCounter(ctx: Context, remMb: Long) {
            val p = Prefs(ctx)
            p.baselineRemainingMb = remMb
            p.baselineTime = System.currentTimeMillis()
            p.usedBytes = 0
            p.lastCounter = maxOf(mobileBytes(), 0)
        }

        fun remainingMb(ctx: Context): Long {
            poll(ctx)
            val p = Prefs(ctx)
            return p.baselineRemainingMb - p.usedBytes / (1024 * 1024)
        }

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, RefillService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, RefillService::class.java))

        fun check(ctx: Context) {
            val p = Prefs(ctx)
            if (!p.enabled || p.number.isEmpty()) return
            val now = System.currentTimeMillis()
            if (p.baselineTime == 0L) return // режим «только по SMS от Lidl»
            val rem = try { remainingMb(ctx) } catch (e: Exception) {
                addLog(p, "Ошибка чтения трафика: ${e.message}"); return
            }
            if (rem > p.thresholdMb) return
            if (now - p.lastSentTime < 3 * 60_000) return
            sendRefill(ctx, "по счётчику осталось ~$rem МБ")
        }

        /** Ищет SIM Lidl: сохранённая (выбрана вручную или пришло SMS от Lidl) или по имени оператора. -1 = не найдена. */
        fun lidlSubId(ctx: Context): Int {
            val p = Prefs(ctx)
            if (p.subId >= 0) return p.subId
            try {
                ctx.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
                    ?.firstOrNull {
                        "${it.displayName} ${it.carrierName}".contains("lidl", ignoreCase = true)
                    }?.let {
                        p.subId = it.subscriptionId
                        addLog(p, "SIM Lidl найдена по имени: ${it.displayName}")
                        return it.subscriptionId
                    }
            } catch (_: SecurityException) {}
            return -1
        }

        fun sendRefill(ctx: Context, reason: String) {
            val p = Prefs(ctx)
            val now = System.currentTimeMillis()
            val day = now / 86_400_000
            if (p.dayKey != day) { p.dayKey = day; p.sentToday = 0 }
            if (p.maxPerDay > 0 && p.sentToday >= p.maxPerDay) {
                addLog(p, "Лимит ${p.maxPerDay} Refill в сутки достигнут, SMS не отправлено")
                p.lastSentTime = now
                return
            }
            val sub = lidlSubId(ctx)
            if (sub < 0) {
                // Никогда не шлём с другой SIM (Kyivstar и т.п.).
                addLog(p, "SIM Lidl не выбрана — SMS НЕ отправлено. Выбери SIM Lidl в настройках")
                p.lastSentTime = now
                return
            }
            try {
                @Suppress("DEPRECATION")
                val sent = PendingIntent.getBroadcast(
                    ctx, 0, Intent(ctx, SmsSentReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                SmsManager.getSmsManagerForSubscriptionId(sub).sendTextMessage(p.number, null, p.text, sent, null)
            } catch (e: Exception) {
                addLog(p, "Не удалось отправить SMS: ${e.message}"); return
            }
            p.sentToday += 1
            p.lastSentTime = now
            addLog(p, "Отправляю «${p.text}» на ${p.number} ($reason)…")
        }

        private fun matches(pattern: String, body: String): Boolean {
            val norm = { s: String -> s.lowercase().replace(Regex("\\s+"), " ").trim() }
            return pattern.isNotBlank() && norm(body).contains(norm(pattern))
        }
        fun isWarning(p: Prefs, body: String) = matches(p.warnText, body)

        /** Пришло SMS «Nachbuchoption ist jetzt gebucht». Остаток уже сброшен при отправке — только пишем в журнал. */
        fun onConfirmed(ctx: Context) {
            val p = Prefs(ctx)
            p.awaitingConfirm = false
            addLog(p, "Lidl подтвердил пополнение")
        }

        fun isConfirmation(p: Prefs, body: String) = matches(p.confirmText, body)

        fun addLog(p: Prefs, msg: String) {
            val t = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
            p.log = (listOf("$t  $msg") + p.log.lines().filter { it.isNotBlank() }).take(30).joinToString("\n")
        }
    }
}
