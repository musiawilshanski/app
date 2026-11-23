package com.mila.translator

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

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
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    @Volatile private var searchVersion = 0
    private var englishIndex: Map<String, Entry> = emptyMap()
    private var hebrewIndex: Map<String, Entry> = emptyMap()
    private var compact = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val focused = currentFocus
        val count = if (::results.isInitialized) results.childCount else 0
        val focusedIndex = if (focused != null && ::results.isInitialized) {
            (0 until results.childCount).firstOrNull { results.getChildAt(it) === focused } ?: -1
        } else -1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focused === input && count > 0) { results.getChildAt(0).requestFocus(); return true }
                if (focusedIndex >= 0 && focusedIndex < count - 1) { results.getChildAt(focusedIndex + 1).requestFocus(); return true }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (focusedIndex == 0) { input.requestFocus(); input.setSelection(input.text.length); return true }
                if (focusedIndex > 0) { results.getChildAt(focusedIndex - 1).requestFocus(); return true }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (focused === input) { translateNow(); if (count > 0) results.getChildAt(0).requestFocus(); return true }
                if (focusedIndex >= 0) { focused?.performClick(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = bg
            window.navigationBarColor = bg
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        compact = resources.displayMetrics.widthPixels / resources.displayMetrics.density < 330f
        loadDictionary()
        buildUi()
    }

    private fun loadDictionary() {
        val base = assets.open("dictionary.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val p = line.split('\t')
                if (p.size >= 2 && p[0].isNotBlank() && p[1].isNotBlank()) Entry(p[0].trim(), p[1].trim()) else null
            }.toList()
        }
        val custom = getSharedPreferences("mila_dictionary", Context.MODE_PRIVATE)
            .getStringSet("custom_words", emptySet()) ?: emptySet()
        val extra = custom.mapNotNull { line ->
            val p = line.split('\t')
            if (p.size == 2 && p[0].isNotBlank() && p[1].isNotBlank()) Entry(p[0], p[1]) else null
        }
        entries = (base + extra).distinctBy { it.en.toLowerCase(Locale.ROOT) + "\t" + it.he }
        englishIndex = entries.associateBy { it.en.toLowerCase(Locale.ROOT) }
        hebrewIndex = entries.associateBy { it.he.toLowerCase(Locale.ROOT) }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = if (compact) size.coerceAtMost(18f) else size
        setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun buildUi() {
        val pad = if (compact) 12 else 20
        val gap = if (compact) 9 else 18
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; isVerticalScrollBarEnabled = false }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(pad), dp(if (compact) 10 else 18), dp(pad), dp(16)) }
        scroll.addView(page); setContentView(scroll)
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val logo = TextView(this).apply {
            text = "מ"; textSize = if (compact) 18f else 23f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = rounded(purple, dp(12).toFloat())
        }
        val logoSize = if (compact) 34 else 42
        top.addView(logo, LinearLayout.LayoutParams(dp(logoSize), dp(logoSize)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),0,0,0) }
        titleBox.addView(text("מילה", if (compact) 20f else 24f, ink, true))
        titleBox.addView(text("מילון אופליין", 11f, muted))
        top.addView(titleBox, LinearLayout.LayoutParams(0,-2,1f))
        val offline = TextView(this).apply {
            text = "● אופליין"; textSize = 10f; setTextColor(Color.rgb(35,145,105))
            setPadding(dp(7),dp(6),dp(7),dp(6)); background = rounded(Color.rgb(226,247,239),dp(20).toFloat())
        }
        top.addView(offline); page.addView(top); page.addView(space(gap))
        if (!compact) {
            page.addView(text("מתרגמים בקלות",24f,ink,true))
            page.addView(text("מילים וביטויים שימושיים, בלי אינטרנט",13f,muted))
            page.addView(space(14))
        }
        val direction = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER
            background=rounded(Color.WHITE,dp(14).toFloat()); setPadding(dp(5),dp(4),dp(5),dp(4))
            isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        }
        val from = TextView(this).apply { gravity=Gravity.CENTER; textSize=if (compact) 12f else 14f; typeface=Typeface.DEFAULT_BOLD }
        val swap = TextView(this).apply {
            text="⇄"; textSize=19f; setTextColor(purple); gravity=Gravity.CENTER
            background=rounded(Color.rgb(242,240,255),dp(10).toFloat())
        }
        val to = TextView(this).apply { gravity=Gravity.CENTER; textSize=if (compact) 12f else 14f; typeface=Typeface.DEFAULT_BOLD }
        fun updateDirection() {
            from.text = if (englishToHebrew) "English" else "עברית"
            to.text = if (englishToHebrew) "עברית" else "English"
            directionText.text = if (englishToHebrew) "אנגלית ← לעברית" else "עברית ← לאנגלית"
        }
        direction.addView(from,LinearLayout.LayoutParams(0,dp(38),1f))
        direction.addView(swap,LinearLayout.LayoutParams(dp(38),dp(38)))
        direction.addView(to,LinearLayout.LayoutParams(0,dp(38),1f))
        page.addView(direction)
        direction.setOnClickListener { englishToHebrew = !englishToHebrew; updateDirection(); translateNow() }
        page.addView(space(gap))
        val inputCard = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; background=rounded(Color.WHITE,dp(16).toFloat())
            setPadding(dp(if (compact) 12 else 16),dp(11),dp(if (compact) 12 else 16),dp(10))
        }
        directionText = text("אנגלית ← לעברית",11f,muted,true); inputCard.addView(directionText)
        input = EditText(this).apply {
            textSize=if (compact) 17f else 19f; setTextColor(ink)
            setHintTextColor(Color.rgb(166,174,190)); hint="הקלידו מילה..."
            background=null; minLines=1; maxLines=2
            setPadding(0,dp(6),0,dp(5)); textDirection=View.TEXT_DIRECTION_FIRST_STRONG
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isSingleLine = false
        }
        inputCard.addView(input,LinearLayout.LayoutParams(-1,-2))
        val actions = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; orientation=LinearLayout.HORIZONTAL }
        val clear = TextView(this).apply { text="ניקוי"; textSize=12f; setTextColor(muted); gravity=Gravity.CENTER }
        val search = TextView(this).apply {
            text="תרגום"; textSize=13f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
            gravity=Gravity.CENTER; background=rounded(purple,dp(11).toFloat())
            setPadding(dp(16),dp(9),dp(16),dp(9))
        }
        actions.addView(clear,LinearLayout.LayoutParams(0,dp(38),1f)); actions.addView(search)
        inputCard.addView(actions)
        val addWord = TextView(this).apply {
            text="+ הוספת מילה למילון"; textSize=12f; setTextColor(purple); gravity=Gravity.CENTER
            setPadding(0,dp(8),0,0)
        }
        inputCard.addView(addWord)
        page.addView(inputCard)
        clear.setOnClickListener {
            input.setText(""); resultText.text=""; hintText.text="התרגום יופיע כאן"
            results.removeAllViews(); input.requestFocus()
        }
        search.setOnClickListener { translateNow(); hideKeyboard() }
        addWord.setOnClickListener { showAddWordDialog() }
        input.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s: CharSequence?, start:Int,before:Int,count:Int) { scheduleTranslation() }
            override fun afterTextChanged(s: Editable?) {}
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { translateNow(); hideKeyboard(); true } else false
        }
        page.addView(space(gap))
        val resultCard = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; background=rounded(Color.rgb(239,237,255),dp(16).toFloat())
            setPadding(dp(14),dp(11),dp(14),dp(11))
        }
        resultCard.addView(text("התרגום שלך",12f,purple,true))
        resultText = text(" ",if (compact) 20f else 24f,ink,true)
        resultText.setPadding(0,dp(6),0,dp(3)); resultCard.addView(resultText)
        hintText = text("התרגום יופיע כאן",11f,muted); resultCard.addView(hintText)
        page.addView(resultCard); page.addView(space(gap))
        val header = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(text("תוצאות נוספות",15f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text(entries.size.toString() + " ערכים",10f,muted))
        page.addView(header); page.addView(space(6))
        results = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; page.addView(results)
        updateDirection()
        showSuggestions(entries.take(5))
    }

    private fun scheduleTranslation() {
        pendingSearch?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable { translateNow() }
        pendingSearch = task
        mainHandler.postDelayed(task, 280)
    }

    private fun translateNow() {
        if (!::resultText.isInitialized) return
        pendingSearch?.let { mainHandler.removeCallbacks(it) }
        val q = input.text.toString().trim()
        val version = ++searchVersion
        if (q.isEmpty()) {
            resultText.text = ""; hintText.text = "התרגום יופיע כאן"
            showSuggestions(entries.take(5)); return
        }
        val key = q.toLowerCase(Locale.ROOT)
        val exact = if (englishToHebrew) englishIndex[key] else hebrewIndex[key]
        if (exact != null) {
            resultText.text = if (englishToHebrew) exact.he else exact.en
            hintText.text = if (englishToHebrew) exact.en else exact.he
            showSuggestions(listOf(exact)); return
        }
        resultText.text = "מחפש..."
        worker.execute {
            val matches = entries.asSequence()
                .filter { if (englishToHebrew) it.en.contains(q, true) else it.he.contains(q, true) }
                .take(10).toList()
            mainHandler.post {
                if (version != searchVersion || isFinishing) return@post
                resultText.text = "לא נמצאה התאמה מדויקת"
                hintText.text = if (matches.isEmpty()) "נסו מילה אחרת" else "הנה תוצאות דומות"
                showSuggestions(matches)
            }
        }
    }

    private fun showAddWordDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20),dp(8),dp(20),0)
        }
        val en = EditText(this).apply { hint = "המילה באנגלית"; setSingleLine(true); textSize = 16f }
        val he = EditText(this).apply { hint = "התרגום בעברית"; setSingleLine(true); textSize = 16f }
        box.addView(en); box.addView(he)
        val dialog = AlertDialog.Builder(this)
            .setTitle("הוספת מילה חדשה")
            .setView(box)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("שמירה", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val english = en.text.toString().trim()
                val hebrew = he.text.toString().trim()
                if (english.isEmpty() || hebrew.isEmpty()) {
                    Toast.makeText(this, "צריך למלא את שני השדות", Toast.LENGTH_SHORT).show()
                } else {
                    val saved = getSharedPreferences("mila_dictionary", Context.MODE_PRIVATE)
                        .getStringSet("custom_words", emptySet())?.toMutableSet() ?: mutableSetOf()
                    saved.add(english + "\t" + hebrew)
                    getSharedPreferences("mila_dictionary", Context.MODE_PRIVATE).edit()
                        .putStringSet("custom_words", saved).apply()
                    loadDictionary()
                    resultText.text = hebrew
                    hintText.text = english + " · נשמר במילון"
                    showSuggestions(listOf(Entry(english, hebrew)))
                    Toast.makeText(this, "המילה נוספה למילון", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showSuggestions(items: List<Entry>) {
        if (!::results.isInitialized) return
        results.removeAllViews()
        if (items.isEmpty()) {
            results.addView(text("לא נמצאו תוצאות. נסו מילה אחרת.",12f,muted)); return
        }
        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
                isFocusable = true; isFocusableInTouchMode = true; isClickable = true
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), rounded(Color.rgb(235,232,255),dp(12).toFloat()))
                    addState(intArrayOf(android.R.attr.state_pressed), rounded(Color.rgb(235,232,255),dp(12).toFloat()))
                    addState(intArrayOf(), rounded(Color.WHITE,dp(12).toFloat()))
                }
                setPadding(dp(12),dp(if (compact) 8 else 11),dp(12),dp(if (compact) 8 else 11))
            }
            val left = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            left.addView(text(if (englishToHebrew) item.en else item.he,if (compact) 14f else 16f,ink,true))
            left.addView(text(if (englishToHebrew) item.he else item.en,if (compact) 12f else 14f,muted))
            row.addView(left,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(text("↗",16f,purple,true))
            row.setOnClickListener {
                input.setText(if (englishToHebrew) item.en else item.he)
                input.setSelection(input.text.length)
                resultText.text = if (englishToHebrew) item.he else item.en
                hintText.text = if (englishToHebrew) item.en else item.he
                hideKeyboard()
            }
            val lp=LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(5); results.addView(row,lp)
        }
    }

    private fun space(h:Int)=View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken,0)
    }

    override fun onDestroy() {
        pendingSearch?.let { mainHandler.removeCallbacks(it) }
        worker.shutdownNow()
        super.onDestroy()
    }
}