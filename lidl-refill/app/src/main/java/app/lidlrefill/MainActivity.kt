package app.lidlrefill

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var number: EditText
    private lateinit var text: EditText
    private lateinit var pack: EditText
    private lateinit var thr: EditText
    private lateinit var maxDay: EditText
    private lateinit var remaining: EditText
    private lateinit var confirm: EditText
    private lateinit var status: TextView
    private lateinit var toggle: Button

    private fun field(root: LinearLayout, label: String, value: String, numeric: Boolean): EditText {
        root.addView(TextView(this).apply { this.text = label; setPadding(0, 24, 0, 0) })
        val e = EditText(this)
        e.setText(value)
        if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(e)
        return e
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 24, 40, 40) }
        setContentView(ScrollView(this).apply { addView(root) })

        number = field(root, "Номер, куда слать SMS (из инструкции Lidl)", p.number, false)
        number.inputType = InputType.TYPE_CLASS_PHONE
        text = field(root, "Текст SMS", p.text, false)
        pack = field(root, "Сколько МБ даёт один Refill", p.packMb.toString(), true)
        thr = field(root, "Слать Refill, когда осталось ≤ (МБ)", p.thresholdMb.toString(), true)
        maxDay = field(root, "Максимум Refill в сутки (0 = без ограничений)", p.maxPerDay.toString(), true)
        confirm = field(root, "Текст SMS-подтверждения от Lidl (часть)", p.confirmText, false)
        remaining = field(root, "Сколько МБ осталось СЕЙЧАС (посмотри в приложении оператора)", "", true)

        root.addView(Button(this).apply {
            this.text = "1. Выдать разрешение на статистику трафика"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        })
        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        root.addView(toggle)
        status = TextView(this).apply { setPadding(0, 32, 0, 0) }
        root.addView(status)
        refreshUi()
    }

    override fun onResume() { super.onResume(); refreshUi() }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun save() {
        p.number = number.text.toString()
        p.text = text.text.toString().ifBlank { "Refill" }
        p.packMb = pack.text.toString().toLongOrNull() ?: 1024
        p.thresholdMb = thr.text.toString().toLongOrNull() ?: 50
        p.confirmText = confirm.text.toString()
        p.maxPerDay = maxDay.text.toString().toIntOrNull() ?: 0
    }

    private fun onToggle() {
        if (p.enabled) {
            p.enabled = false
            RefillService.stop(this)
            refreshUi(); return
        }
        save()
        val rem = remaining.text.toString().toLongOrNull()
        when {
            p.number.isEmpty() -> toast("Введи номер для SMS")
            rem == null -> toast("Введи текущий остаток МБ")
            !hasUsageAccess() -> toast("Сначала выдай доступ к статистике трафика (кнопка 1)")
            else -> {
                val need = mutableListOf<String>()
                if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                    need += Manifest.permission.SEND_SMS
                if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
                    need += Manifest.permission.RECEIVE_SMS
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    need += Manifest.permission.POST_NOTIFICATIONS
                if (need.isNotEmpty()) {
                    requestPermissions(need.toTypedArray(), 1)
                    toast("Разреши SMS и уведомления, затем нажми ещё раз"); return
                }
                p.baselineRemainingMb = rem
                p.baselineTime = System.currentTimeMillis()
                p.awaitingConfirm = false
                p.lastSentTime = 0
                p.enabled = true
                RefillService.start(this)
            }
        }
        refreshUi()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun refreshUi() {
        toggle.text = if (p.enabled) "Остановить" else "2. Запустить"
        val est = if (p.enabled) try { "≈ ${RefillService.remainingMb(this)} МБ" } catch (e: Exception) { "?" } else "—"
        status.text = "Доступ к статистике: ${if (hasUsageAccess()) "есть" else "НЕТ"}\n" +
            "Работает: ${if (p.enabled) "да" else "нет"}\n" +
            "Оценка остатка: $est\n" +
            "Refill сегодня: ${p.sentToday}\n" +
            (if (p.awaitingConfirm) "Жду SMS-подтверждение от Lidl\n" else "") + "\n" + p.log
        status.visibility = View.VISIBLE
    }
}
