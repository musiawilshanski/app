package com.mila.translator

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.*

class MainActivity : Activity() {
    private data class Entry(val en: String, val he: String)
    private var entries: List<Entry> = emptyList()
    private var englishToHebrew = true
    private lateinit var input: EditText
    private lateinit var resultText: TextView
    private lateinit var hintText: TextView
    private lateinit var directionText: TextView
    private lateinit var results: LinearLayout
    private val ink = Color.rgb(28, 39, 61)
    private val muted = Color.rgb(112, 125, 147)
    private val purple = Color.rgb(100, 82, 220)
    private val bg = Color.rgb(246, 247, 252)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        entries = assets.open("dictionary.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val p = line.split('\t')
                if (p.size >= 2) Entry(p[0].trim(), p[1].trim()) else null
            }.toList()
        }
        buildUi()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(24), dp(22), dp(28)) }
        scroll.addView(page); setContentView(scroll)
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val logo = TextView(this).apply { text = "מ"; textSize = 23f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; background = rounded(purple, dp(15).toFloat()) }
        top.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        titleBox.addView(text("מילה",25f,ink,true)); titleBox.addView(text("המילון האישי שלך",12f,muted))
        top.addView(titleBox, LinearLayout.LayoutParams(0,-2,1f))
        val offline = TextView(this).apply { text = "● אופליין"; textSize = 11f; setTextColor(Color.rgb(35,145,105)); setPadding(dp(11),dp(8),dp(11),dp(8)); background = rounded(Color.rgb(226,247,239),dp(30).toFloat()) }
        top.addView(offline); page.addView(top); page.addView(space(24))
        page.addView(text("מתרגמים בקלות",27f,ink,true))
        page.addView(text("מילים וביטויים שימושיים, בלי חיבור לאינטרנט",14f,muted)); page.addView(space(22))
        val direction = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; background=rounded(Color.WHITE,dp(18).toFloat()); setPadding(dp(7),dp(7),dp(7),dp(7)) }
        val from = TextView(this).apply { gravity=Gravity.CENTER; textSize=14f; typeface=Typeface.DEFAULT_BOLD }
        val swap = TextView(this).apply { text="⇄"; textSize=22f; setTextColor(purple); gravity=Gravity.CENTER; background=rounded(Color.rgb(242,240,255),dp(13).toFloat()) }
        val to = TextView(this).apply { gravity=Gravity.CENTER; textSize=14f; typeface=Typeface.DEFAULT_BOLD }
        fun updateDirection() {
            from.text = if (englishToHebrew) "🇬🇧  English" else "🇮🇱  עברית"
            to.text = if (englishToHebrew) "🇮🇱  עברית" else "🇬🇧  English"
            directionText.text = if (englishToHebrew) "אנגלית ← לעברית" else "עברית ← לאנגלית"
        }
        direction.addView(from,LinearLayout.LayoutParams(0,dp(48),1f)); direction.addView(swap,LinearLayout.LayoutParams(dp(48),dp(48))); direction.addView(to,LinearLayout.LayoutParams(0,dp(48),1f))
        page.addView(direction); direction.setOnClickListener { englishToHebrew = !englishToHebrew; updateDirection(); translate() }; page.addView(space(18))
        val inputCard = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(Color.WHITE,dp(20).toFloat()); setPadding(dp(18),dp(16),dp(18),dp(16)) }
        directionText = text("אנגלית ← לעברית",12f,muted,true); inputCard.addView(directionText)
        input = EditText(this).apply { textSize=20f; setTextColor(ink); setHintTextColor(Color.rgb(166,174,190)); hint="הקלידו מילה או ביטוי..."; background=null; minLines=2; maxLines=4; setPadding(0,dp(12),0,dp(8)); textDirection=View.TEXT_DIRECTION_FIRST_STRONG }
        inputCard.addView(input,LinearLayout.LayoutParams(-1,-2))
        val actions = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; orientation=LinearLayout.HORIZONTAL }
        val clear = TextView(this).apply { text="ניקוי"; textSize=13f; setTextColor(muted); gravity=Gravity.CENTER }
        val search = TextView(this).apply { text="תרגום  →"; textSize=14f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; background=rounded(purple,dp(14).toFloat()); setPadding(dp(22),dp(13),dp(22),dp(13)) }
        actions.addView(clear,LinearLayout.LayoutParams(0,dp(46),1f)); actions.addView(search); inputCard.addView(actions); page.addView(inputCard)
        clear.setOnClickListener { input.setText(""); resultText.text=""; hintText.text="התרגום יופיע כאן"; results.removeAllViews() }
        search.setOnClickListener { translate(); hideKeyboard() }
        input.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s: CharSequence?, start:Int,before:Int,count:Int) { translate() }
            override fun afterTextChanged(s: Editable?) {}
        })
        page.addView(space(20))
        val resultCard = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(Color.rgb(239,237,255),dp(20).toFloat()); setPadding(dp(18),dp(17),dp(18),dp(18)) }
        resultCard.addView(text("התרגום שלך",13f,purple,true))
        resultText = text(" ",25f,ink,true); resultText.setPadding(0,dp(10),0,dp(4)); resultCard.addView(resultText)
        hintText = text("התרגום יופיע כאן",12f,muted); resultCard.addView(hintText); page.addView(resultCard); page.addView(space(24))
        val header = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(text("אולי התכוונת ל…",18f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text("${entries.size} ערכים",12f,muted)); page.addView(header); page.addView(space(8))
        results = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; page.addView(results)
        updateDirection(); showSuggestions(entries.take(7))
    }
    private fun translate() {
        if (!::resultText.isInitialized) return
        val q = input.text.toString().trim()
        if (q.isEmpty()) { resultText.text=""; hintText.text="התרגום יופיע כאן"; showSuggestions(entries.take(7)); return }
        val found = entries.firstOrNull { if (englishToHebrew) it.en.equals(q,true) else it.he.equals(q,true) }
        if (found != null) {
            resultText.text = if (englishToHebrew) found.he else found.en
            hintText.text = if (englishToHebrew) found.en else found.he
            showSuggestions(listOf(found))
        } else {
            val matches = entries.filter { if (englishToHebrew) it.en.contains(q,true) else it.he.contains(q,true) }.take(12)
            resultText.text = "לא נמצאה התאמה מדויקת"
            hintText.text = if (matches.isEmpty()) "נסו מילה אחרת או ניסוח קצר יותר" else "הנה תוצאות דומות"
            showSuggestions(matches)
        }
    }
    private fun showSuggestions(items: List<Entry>) {
        if (!::results.isInitialized) return
        results.removeAllViews()
        if (items.isEmpty()) { results.addView(text("לא נמצאו תוצאות. נסו מילה אחרת.",14f,muted)); return }
        items.forEach { item ->
            val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; background=rounded(Color.WHITE,dp(14).toFloat()); setPadding(dp(15),dp(13),dp(15),dp(13)) }
            val left = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            left.addView(text(if (englishToHebrew) item.en else item.he,16f,ink,true))
            left.addView(text(if (englishToHebrew) item.he else item.en,14f,muted))
            row.addView(left,LinearLayout.LayoutParams(0,-2,1f)); row.addView(text("↗",18f,purple,true))
            row.setOnClickListener {
                input.setText(if (englishToHebrew) item.en else item.he); input.setSelection(input.text.length)
                resultText.text = if (englishToHebrew) item.he else item.en
                hintText.text = if (englishToHebrew) item.en else item.he; hideKeyboard()
            }
            val lp=LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(8); results.addView(row,lp)
        }
    }
    private fun space(h:Int)=View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
        input.clearFocus()
    }
}
