package il.shomer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Small view helpers and the colors of the design (paper, pine, red). The screens are built in code, so these keep them short. */
object Ui {
    val PAPER = Color.parseColor("#E8EEE6")
    val SHEET = Color.parseColor("#FBFBF7")
    val INK = Color.parseColor("#16302A")
    val SOFT = Color.parseColor("#41564E")
    val PINE = Color.parseColor("#1D4B41")
    val RED = Color.parseColor("#9E2320")
    val RED_BG = Color.parseColor("#F8E9E5")
    val BRONZE = Color.parseColor("#76500A")
    val BRONZE_BG = Color.parseColor("#F7EDD2")
    val INDIGO = Color.parseColor("#224C8C")
    val INDIGO_BG = Color.parseColor("#E5ECF7")
    val WASH = Color.parseColor("#DCE5DA")
    val LINE = Color.parseColor("#CBD6CB")
    val LINE_STRONG = Color.parseColor("#64776D")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun shape(c: Context, fill: Int, radiusDp: Int, stroke: Int? = null, strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(c, radiusDp).toFloat()
        if (stroke != null) setStroke(dp(c, strokeDp), stroke)
    }

    fun lp(c: Context, topDp: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(c, topDp) }

    fun text(c: Context, t: String, sp: Float = 17f, bold: Boolean = false, color: Int = INK) = TextView(c).apply {
        text = t; textSize = sp; setTextColor(color); textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    /** A rounded panel. Children are added by the caller. */
    fun card(c: Context, fill: Int = SHEET, stroke: Int? = LINE, strokeDp: Int = 1) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(c, 16); setPadding(p, p, p, p)
        background = shape(c, fill, 18, stroke, strokeDp)
    }

    /** kind: "primary" (pine), "ghost" (outlined), "red". Minimum height 56dp so it is easy to hit. */
    fun button(c: Context, label: String, kind: String = "primary", onClick: () -> Unit) = Button(c).apply {
        text = label; textSize = 17f; isAllCaps = false; setTypeface(typeface, Typeface.BOLD); minHeight = dp(c, 56); minimumHeight = dp(c, 56)
        gravity = Gravity.CENTER
        when (kind) {
            "primary" -> { setTextColor(Color.WHITE); background = shape(c, PINE, 14) }
            "red" -> { setTextColor(Color.WHITE); background = shape(c, RED, 14) }
            else -> { setTextColor(INK); background = shape(c, SHEET, 14, LINE_STRONG, 2) }
        }
        setOnClickListener { onClick() }
    }

    /** The round "ש" mark. */
    fun mark(c: Context, sizeDp: Int = 38) = TextView(c).apply {
        text = "ש"; setTextColor(PINE); textSize = sizeDp * 0.42f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
        background = GradientDrawable().apply { setShape(GradientDrawable.OVAL); setColor(Color.TRANSPARENT); setStroke(dp(c, 3), PINE) }
        layoutParams = LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))
    }
}
