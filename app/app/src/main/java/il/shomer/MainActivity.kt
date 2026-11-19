package il.shomer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * One screen: paste or share a message to check it, plus a one-time setup (consent for SMS checking,
 * optional address of the family server). Large text, one main button.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        fun label(t: String, sp: Float = 22f) = TextView(this).apply { text = t; textSize = sp; textAlignment = View.TEXT_ALIGNMENT_VIEW_START }

        col.addView(label("שומר", 30f))
        col.addView(label("הדביקו הודעה, או שתפו אליי הודעה מכל אפליקציה:"))
        input = EditText(this).apply { textSize = 22f; minLines = 4; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        col.addView(input)
        col.addView(Button(this).apply { text = "בדקו לי"; textSize = 26f; setOnClickListener { runCheck() } })
        output = label("")
        col.addView(output)
        col.addView(Button(this).apply { text = "הפעלת בדיקה אוטומטית של SMS"; textSize = 18f; setOnClickListener { askConsent() } })
        val server = EditText(this).apply {
            hint = "כתובת שרת המשפחה (אופציונלי)"; textSize = 18f
            setText(getSharedPreferences("shomer", MODE_PRIVATE).getString("server_url", ""))
        }
        col.addView(server)
        col.addView(Button(this).apply {
            text = "שמירה"; textSize = 18f
            setOnClickListener { getSharedPreferences("shomer", MODE_PRIVATE).edit().putString("server_url", server.text.toString().trim()).apply() }
        })
        setContentView(ScrollView(this).apply { addView(col) })

        if (intent?.action == Intent.ACTION_SEND) {
            input.setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            runCheck()
        }
    }

    private fun runCheck() {
        val r = Checker.engine(this).check(input.text.toString())
        output.text = if (!r.applicable) "אין בהודעה קישור או גוף מוכר לבדוק."
        else (listOf(r.headline) + r.reasons + r.advice).joinToString("\n\n")
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
}
