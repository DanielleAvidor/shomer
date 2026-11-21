package il.shomer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

/** The full explanation of one alert. It opens from the Shomer notification, from the list on the home screen, and from a manual check. */
class ExplainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "שומר"
        val headline = intent.getStringExtra(HEADLINE).orEmpty()
        val advice = intent.getStringExtra(ADVICE).orEmpty()
        val reasons = intent.getStringArrayExtra(REASONS)?.toList().orEmpty()
        val url = intent.getStringExtra(URL).orEmpty()
        val verdict = intent.getStringExtra(VERDICT).orEmpty()
        val (fg, bg) = when (verdict) {
            Verdict.DONT_CLICK.key -> Ui.RED to Ui.RED_BG
            Verdict.MATCHES_OFFICIAL.key -> Ui.INDIGO to Ui.INDIGO_BG
            else -> Ui.BRONZE to Ui.BRONZE_BG
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setBackgroundColor(Ui.PAPER)
            val p = Ui.dp(context, 20); setPadding(p, p, p, p)
        }
        col.addView(Ui.card(this, bg, fg, 3).apply { addView(Ui.text(context, headline, 30f, true, fg)) }, Ui.lp(this))
        col.addView(Ui.text(this, advice, 19f, true), Ui.lp(this, 16))
        if (url.isNotBlank()) col.addView(Ui.button(this, "לפתוח את האתר הרשמי") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }, Ui.lp(this, 16))
        col.addView(Ui.button(this, "לשלוח לבן משפחה", "ghost") {
            val share = "בדקתי הודעה עם שומר. התוצאה: $headline. אפשר להסתכל על זה יחד?"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, share) }, null))
        }, Ui.lp(this, 10))
        if (reasons.isNotEmpty()) {
            val why = Ui.card(this).apply {
                addView(Ui.text(context, "למה", 18f, true))
                for (r in reasons) addView(Ui.text(context, "• $r", 16f, false, Ui.SOFT), Ui.lp(context, 6))
            }
            col.addView(why, Ui.lp(this, 16))
        }
        col.addView(Ui.button(this, "חזרה", "ghost") { finish() }, Ui.lp(this, 16))
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.PAPER); addView(col) })
    }

    companion object {
        private const val HEADLINE = "headline"; private const val ADVICE = "advice"; private const val REASONS = "reasons"
        private const val URL = "url"; private const val VERDICT = "verdict"

        fun intentFor(c: Context, r: CheckResult) = Intent(c, ExplainActivity::class.java)
            .putExtra(HEADLINE, r.headline).putExtra(ADVICE, r.advice).putExtra(REASONS, r.reasons.take(4).toTypedArray())
            .putExtra(URL, r.officialUrl ?: "").putExtra(VERDICT, r.verdict.key)

        fun intentFor(c: Context, e: History.Entry) = Intent(c, ExplainActivity::class.java)
            .putExtra(HEADLINE, e.headline).putExtra(ADVICE, e.advice).putExtra(REASONS, e.reasons.toTypedArray())
            .putExtra(URL, e.officialUrl).putExtra(VERDICT, e.verdict)
    }
}
