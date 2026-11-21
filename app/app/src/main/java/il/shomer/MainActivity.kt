package il.shomer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Four tabs: Home (is Shomer working, what it found), Check (paste or share a message), Family and Settings.
 * The screens are built in code. The important thing is on the lock screen, not here: this app is the place to look back,
 * to set things up, and to check a message by hand.
 */
class MainActivity : AppCompatActivity() {
    private enum class Tab(val label: String) { HOME("בית"), CHECK("בדיקה"), FAMILY("משפחה"), SETTINGS("הגדרות") }

    private var tab = Tab.HOME
    private lateinit var body: FrameLayout
    private lateinit var nav: LinearLayout
    private var pastedText = ""
    private var lastResult: CheckResult? = null
    private var openListenerSettingsAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "שומר"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(Ui.PAPER) }
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.SHEET) }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        if (intent?.action == Intent.ACTION_SEND) {
            pastedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            tab = Tab.CHECK
            lastResult = Checker.engine(this).check(pastedText)
        }
        render()
    }

    override fun onResume() { super.onResume(); render() }

    // ---------- state of the agent ----------
    private fun notificationsOn() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun smsOn() = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    private fun listenerOn() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    // ---------- frame ----------
    private fun render() {
        nav.removeAllViews()
        for (t in Tab.values()) {
            val on = t == tab
            nav.addView(TextView(this).apply {
                text = t.label; textSize = 16f; gravity = Gravity.CENTER; minimumHeight = Ui.dp(context, 56)
                setTextColor(if (on) Ui.PINE else Ui.SOFT); setTypeface(typeface, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                contentDescription = t.label + if (on) ", נבחר" else ""
                isClickable = true; isFocusable = true
                setOnClickListener { tab = t; render() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val p = Ui.dp(context, 20); setPadding(p, p, p, p) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(Ui.mark(this)); header.addView(Ui.text(this, "  שומר", 30f, true))
        col.addView(header, Ui.lp(this))
        when (tab) { Tab.HOME -> home(col); Tab.CHECK -> check(col); Tab.FAMILY -> family(col); Tab.SETTINGS -> settings(col) }
        body.removeAllViews()
        body.addView(ScrollView(this).apply { addView(col) })
    }

    // ---------- home ----------
    private fun home(col: LinearLayout) {
        val watching = listOfNotNull(if (smsOn()) "הודעות SMS" else null, if (listenerOn()) "וואטסאפ והודעות נוספות" else null)
        val (checked, alerts) = History.todayCounts(this)
        val on = watching.isNotEmpty() && notificationsOn()
        val status = Ui.card(this).apply {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(View(context).apply { background = GradientDrawable().apply { setShape(GradientDrawable.OVAL); setColor(if (on) Ui.PINE else Ui.BRONZE) } }, LinearLayout.LayoutParams(Ui.dp(context, 14), Ui.dp(context, 14)))
            row.addView(Ui.text(context, "  " + if (on) "שומר פועל ברקע" else if (watching.isEmpty()) "שומר עוד לא עוקב" else "שומר בודק, אבל לא יתריע", 22f, true))
            addView(row)
            addView(Ui.text(context, when {
                on -> "בודק: " + watching.joinToString(" ו") + ". מתריע רק כשיש סיבה."
                watching.isEmpty() -> "אפשר להפעיל למטה, או לבדוק הודעה ידנית בלשונית בדיקה."
                else -> "אין לשומר הרשאה להציג התראות, ולכן לא תראו אותן. אפשר לאשר בהגדרות."
            }, 16f, false, Ui.SOFT), Ui.lp(context, 4))
            val nums = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            fun stat(label: String, n: Int) = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; addView(Ui.text(context, label, 15f, false, Ui.SOFT)); addView(Ui.text(context, n.toString(), 32f, true))
            }
            nums.addView(stat("נבדקו היום", checked), LinearLayout.LayoutParams(0, -2, 1f))
            nums.addView(stat("התראות", alerts), LinearLayout.LayoutParams(0, -2, 1f))
            addView(nums, Ui.lp(context, 12))
        }
        col.addView(status, Ui.lp(this, 16))
        if (!smsOn()) col.addView(Ui.button(this, "להפעיל בדיקה של הודעות SMS") { askConsent() }, Ui.lp(this, 12))
        if (!listenerOn()) col.addView(Ui.button(this, "להפעיל בדיקה של וואטסאפ והודעות נוספות", "ghost") { askListenerConsent() }, Ui.lp(this, 12))
        col.addView(Ui.text(this, "הודעות אחרונות", 19f, true), Ui.lp(this, 20))
        val items = History.all(this).take(10)
        if (items.isEmpty()) col.addView(Ui.text(this, if (History.enabled(this)) "עדיין לא נבדקו הודעות." else "שמירת הרשימה כבויה בהגדרות.", 16f, false, Ui.SOFT), Ui.lp(this, 8))
        for (e in items) col.addView(historyRow(e), Ui.lp(this, 10))
    }

    private fun historyRow(e: History.Entry): View {
        val card = if (e.alert) Ui.card(this, Ui.RED_BG, Ui.RED, 2) else Ui.card(this)
        card.addView(Ui.text(this, "${e.app} · ${History.timeLabel(e.ts)}", 14f, false, Ui.SOFT))
        card.addView(Ui.text(this, if (e.alert) "התראה: ${e.headline}" else "נבדקה. לא היה צורך להתריע", 17f, e.alert, if (e.alert) Ui.RED else Ui.INK), Ui.lp(this, 4))
        if (e.alert) {
            card.isClickable = true; card.isFocusable = true
            card.contentDescription = "התראה: ${e.headline}. לחיצה פותחת הסבר"
            card.setOnClickListener { startActivity(ExplainActivity.intentFor(this, e)) }
        }
        return card
    }

    // ---------- manual check ----------
    private fun check(col: LinearLayout) {
        col.addView(Ui.text(this, "הדביקו הודעה, או שתפו אליי הודעה מכל אפליקציה:", 18f), Ui.lp(this, 16))
        val input = EditText(this).apply {
            textSize = 20f; minLines = 4; gravity = Gravity.TOP or Gravity.START; setText(pastedText)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Ui.INK); background = Ui.shape(context, Ui.SHEET, 14, Ui.LINE_STRONG, 2)
            val p = Ui.dp(context, 14); setPadding(p, p, p, p)
        }
        col.addView(input, Ui.lp(this, 8))
        val out = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun show(r: CheckResult) {
            out.removeAllViews()
            val (fg, bg) = when (r.verdict) { Verdict.DONT_CLICK -> Ui.RED to Ui.RED_BG; Verdict.MATCHES_OFFICIAL -> Ui.INDIGO to Ui.INDIGO_BG; else -> Ui.BRONZE to Ui.BRONZE_BG }
            out.addView(Ui.card(this, bg, fg, 3).apply { addView(Ui.text(context, r.headline, 24f, true, fg)) }, Ui.lp(this, 16))
            out.addView(Ui.text(this, r.advice, 17f, true), Ui.lp(this, 12))
            if (r.applicable) out.addView(Ui.button(this, "ההסבר המלא", "ghost") { startActivity(ExplainActivity.intentFor(this, r)) }, Ui.lp(this, 12))
        }
        col.addView(Ui.button(this, "בדקו לי", "primary") {
            pastedText = input.text.toString()
            val r = Checker.engine(this).check(pastedText); lastResult = r; show(r)
        }, Ui.lp(this, 12))
        col.addView(out)
        lastResult?.let { show(it) }
    }

    // ---------- family ----------
    private fun family(col: LinearLayout) {
        col.addView(Ui.text(this, "המשפחה", 24f, true), Ui.lp(this, 16))
        col.addView(Ui.text(this, "אפשר לשלוח לבן משפחה עדכון כשהודעה כמעט בוודאות הונאה. זה כבוי עד שמגדירים כתובת שרת.", 17f, false, Ui.SOFT), Ui.lp(this, 6))
        val server = EditText(this).apply {
            hint = "כתובת שרת המשפחה"; textSize = 18f; setTextColor(Ui.INK)
            setText(getSharedPreferences("shomer", MODE_PRIVATE).getString("server_url", ""))
            background = Ui.shape(context, Ui.SHEET, 14, Ui.LINE_STRONG, 2); val p = Ui.dp(context, 14); setPadding(p, p, p, p)
        }
        col.addView(server, Ui.lp(this, 14))
        col.addView(Ui.button(this, "שמירה") { getSharedPreferences("shomer", MODE_PRIVATE).edit().putString("server_url", server.text.toString().trim()).apply() }, Ui.lp(this, 10))
        col.addView(Ui.card(this).apply {
            addView(Ui.text(context, "מה נשלח, ומה לא", 18f, true))
            addView(Ui.text(context, "נשלח: סוג התוצאה, שם הגוף שהוזכר, שם האתר החשוד, והאם זו הונאה כמעט בטוחה.", 16f, false, Ui.SOFT), Ui.lp(context, 6))
            addView(Ui.text(context, "לא נשלח: טקסט ההודעה ופרטי השולח.", 16f, false, Ui.SOFT), Ui.lp(context, 4))
        }, Ui.lp(this, 16))
    }

    // ---------- settings ----------
    private fun settings(col: LinearLayout) {
        col.addView(Ui.text(this, "הגדרות", 24f, true), Ui.lp(this, 16))
        fun row(title: String, state: String, action: String?, onClick: () -> Unit) {
            col.addView(Ui.card(this).apply {
                addView(Ui.text(context, title, 18f, true)); addView(Ui.text(context, state, 16f, false, Ui.SOFT), Ui.lp(context, 2))
                if (action != null) addView(Ui.button(context, action, "ghost") { onClick() }, Ui.lp(context, 10))
            }, Ui.lp(this, 12))
        }
        row("הודעות SMS", if (smsOn()) "פועל. שומר בודק כל הודעה שנכנסת." else "כבוי.", if (smsOn()) null else "להפעיל") { askConsent() }
        row("וואטסאפ והודעות נוספות", if (listenerOn()) "פועל. שומר קורא רק התראות של וואטסאפ, טלגרם, ג׳ימייל ומסנג׳ר. לביטול: בהגדרות הטלפון." else "כבוי.", if (listenerOn()) "לפתוח את הגדרות הגישה להתראות" else "להפעיל") { if (listenerOn()) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) else askListenerConsent() }
        row("הצגת התראות", if (notificationsOn()) "מאושר." else "לא מאושר, ולכן שומר לא יכול להתריע.", if (notificationsOn()) null else "לאשר") { if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3) }
        col.addView(Ui.text(this, "פרטיות", 19f, true), Ui.lp(this, 20))
        col.addView(Ui.card(this).apply {
            addView(Ui.text(context, "שומר לא שומר את טקסט ההודעות ולא את שם השולח. כשיש התראה הוא זוכר את ההסבר שלו, כדי שתוכלו לקרוא אותו שוב.", 16f, false, Ui.SOFT))
            val sw = SwitchCompat(context).apply {
                text = "לזכור את הבדיקות האחרונות"; textSize = 17f; isChecked = History.enabled(context)
                setOnCheckedChangeListener { _, on -> History.setEnabled(context, on) }
            }
            addView(sw, Ui.lp(context, 12))
            addView(Ui.button(context, "למחוק את הרשימה", "ghost") { History.clear(context); render() }, Ui.lp(context, 10))
        }, Ui.lp(this, 8))
    }

    // ---------- consent dialogs (unchanged wording) ----------
    private fun askListenerConsent() {
        AlertDialog.Builder(this)
            .setTitle("בדיקה של וואטסאפ והודעות נוספות")
            .setMessage("כדי לבדוק הודעות בוואטסאפ, האפליקציה צריכה גישה להתראות בטלפון. היא תקרא רק התראות של הודעות מוואטסאפ, טלגרם, ג'ימייל ומסנג'ר, ותבדוק אותן בטלפון עצמו. תוכן ההודעות לא נשמר ולא נשלח. שימו לב: ההרשאה הזו נוגעת לכל ההתראות, ושומר מתעלם מהשאר. אפשר לבטל בכל רגע באותו מקום. בעמוד שייפתח, בחרו ב'שומר' והפעילו.")
            .setPositiveButton("מסכימים, לפתוח את ההגדרות") { _, _ ->
                if (!notificationsOn()) { openListenerSettingsAfterPermission = true; requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2) }
                else startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("לא עכשיו", null)
            .show()
    }

    private fun askConsent() {
        AlertDialog.Builder(this)
            .setTitle("בדיקה אוטומטית של הודעות SMS")
            .setMessage("האפליקציה תקרא הודעות SMS שנכנסות כדי לבדוק אם יש בהן קישור מחשיד. הבדיקה נעשית בטלפון עצמו. תוכן ההודעות לא נשמר ולא נשלח. אם הוגדר שרת משפחה, יישלחו אליו רק התוצאה, שם הגוף וכתובת האתר. אפשר לבטל בכל רגע בהגדרות הטלפון.")
            .setPositiveButton("מסכימים") { _, _ ->
                val perms = mutableListOf(Manifest.permission.RECEIVE_SMS)
                if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                requestPermissions(perms.toTypedArray(), 1)
            }
            .setNegativeButton("לא עכשיו", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
        if (openListenerSettingsAfterPermission) { openListenerSettingsAfterPermission = false; startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }
}
