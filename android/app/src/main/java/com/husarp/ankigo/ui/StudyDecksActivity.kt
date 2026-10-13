package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.*
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.update.Updates
import java.util.concurrent.Executors

/** AnkiDroid's own tree counts already include child decks and daily limits. */
class StudyDecksActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("study", 0) }
    private var decks = emptyList<Anki.Deck>()
    private var folded = emptySet<String>()
    private var generation = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Anki.load(this)
        folded = prefs.getStringSet("folded", emptySet())!!.toSet()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@StudyDecksActivity, R.color.ag_base)) }
        val bar = LinearLayout(this)
        bar.addView(Ui.text(this, 26f, 900, R.color.ag_text).apply { text = "Study" }, Form.lp(0, -2, 1f))
        bar.addView(Form.secondary(this, "↻") { refresh() }.apply { contentDescription = "Refresh decks" })
        bar.addView(Form.secondary(this, "Settings") { startActivity(Intent(this@StudyDecksActivity, SettingsActivity::class.java)) })
        root.addView(bar)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 14), Ui.dp(context, 12)) }
        val scroll = ScrollView(this).apply { addView(content) }
        var down = 0f
        scroll.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) down = e.y
            if (e.actionMasked == MotionEvent.ACTION_UP && scroll.scrollY == 0 && e.y - down > Ui.dp(this, 80)) refresh()
            false
        }
        root.addView(scroll, Form.lp(-1, 0, 1f))
        root.addView(BottomNav.create(this, BottomNav.Tab.STUDY))
        setContentView(root)
        GameRepo.load(this) { if (!isDestroyed) render() }
    }
    override fun onStart() { super.onStart(); BattleScreen.setVisible(this, true) }
    override fun onStop() { BattleScreen.setVisible(this, false); super.onStop() }
    override fun onResume() { super.onResume(); Updates.onResume(this); FloatBattleService.startIfWanted(this); refresh() }
    override fun onPause() { Updates.onPause(this); super.onPause() }
    override fun onDestroy() { generation++; io.shutdown(); super.onDestroy() }
    private fun refresh() {
        val token = ++generation
        content.removeAllViews(); content.addView(Form.body(this, "Loading decks…"))
        io.execute {
            val result = runCatching { if (!Anki.hasPermission(this)) error("Allow AnkiGo to use AnkiDroid first."); Anki.decks(this) }
            runOnUiThread {
                if (isDestroyed || token != generation) return@runOnUiThread
                result.onSuccess { decks = it.sortedBy { d -> d.name }; render() }.onFailure {
                    content.removeAllViews(); content.addView(Form.body(this, it.message ?: "Open AnkiDroid once, then refresh."))
                    content.addView(Form.primary(this, "Allow AnkiDroid access") { requestPermissions(arrayOf(Anki.permission), 1) })
                    content.addView(Form.secondary(this, "Open AnkiDroid") { Anki.launchIntent(this)?.let { startActivity(it) } })
                }
            }
        }
    }
    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        if (GameRepo.data == null) content.addView(Form.body(this, "Study and diagnostics work without game data. Download about 4.3 MB in Settings to play battles.", 13))
        val last = decks.firstOrNull { it.id == prefs.getLong("lastDeck", 0) }
        if (last != null) content.addView(Form.primary(this, "Continue · ${last.name} · ${last.total} due") { open(last) }, Form.lp(this, 12))
        GameRepo.state?.let { st ->
            val row = LinearLayout(this)
            val num = st.nextSpecies ?: st.battle?.wild?.species
            if (num != null) row.addView(android.widget.ImageView(this).apply { Ui.staticSprite(context, num, false, this, true) }, Form.lp(Ui.dp(this, 56), Ui.dp(this, 56)))
            row.addView(Form.body(this, "A wild Pokémon is waiting\n${st.reviewsToday} / ${st.settings.dailyAverage} today"), Form.lp(0, -2, 1f))
            content.addView(row, Form.lp(this, 12))
        }
        content.addView(Form.sect(this, "Decks", "tap = here · A = AnkiDroid"))
        if (decks.isEmpty()) content.addView(Form.body(this, "No decks found. Open AnkiDroid once, then refresh."))
        for (deck in decks) {
            val ancestors = deck.name.split("::").dropLast(1).runningReduce { a, b -> "$a::$b" }
            if (ancestors.any { it in folded }) continue
            val children = decks.any { it.name.startsWith(deck.name + "::") }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; minimumHeight = Ui.dp(context, 56); gravity = android.view.Gravity.CENTER_VERTICAL; alpha = if (deck.total == 0) 0.5f else 1f }
            row.setPadding(Ui.dp(this, ancestors.size * 12), 0, 0, 0)
            if (children) row.addView(Form.secondary(this, if (deck.name in folded) "›" else "⌄") {
                folded = if (deck.name in folded) folded - deck.name else folded + deck.name
                prefs.edit().putStringSet("folded", folded).apply(); render()
            }.apply { contentDescription = "Fold ${deck.name}" })
            row.addView(Form.body(this, deck.name.substringAfterLast("::") + if (deck.dyn) " · Filtered" else "", 14, R.color.ag_text).apply {
                minimumHeight = Ui.dp(context, 48); gravity = android.view.Gravity.CENTER_VERTICAL; setOnClickListener { open(deck) }
            }, Form.lp(0, -2, 1f))
            listOf(deck.new to R.color.ag_blue, deck.learn to R.color.ag_red, deck.review to R.color.ag_green).forEachIndexed { i, (n, color) ->
                row.addView(Ui.text(this, 13f, 800, if (n == 0) R.color.ag_sub0 else color).apply {
                    text = n.coerceAtLeast(0).toString(); minWidth = Ui.dp(context, 28)
                    contentDescription = "${listOf("New", "Learn", "Review")[i]} $n"; setOnClickListener { open(deck) }
                })
            }
            row.addView(Form.secondary(this, "A", R.color.ag_blue) { companion(deck) }.apply { contentDescription = "Study ${deck.name} in AnkiDroid" })
            content.addView(row); content.addView(Form.divider(this))
        }
    }
    private fun open(deck: Anki.Deck) {
        if (prefs.getBoolean("inAnkiDroid", false)) { companion(deck); return }
        if (deck.total == 0) {
            AlertDialog.Builder(this).setMessage("Nothing due. Custom study is in AnkiDroid.")
                .setPositiveButton("Open in AnkiDroid") { _, _ -> companion(deck) }.setNegativeButton("Back", null).show(); return
        }
        startActivity(Intent(this, StudyActivity::class.java).putExtra("deckId", deck.id).putExtra("deckName", deck.name))
    }
    private fun companion(deck: Anki.Deck) {
        io.execute {
            val result = runCatching { Anki.selectDeck(this, deck.id) }
            runOnUiThread { if (!isDestroyed) result.onSuccess { runCatching { if (GameRepo.game == null) startActivity(Anki.reviewIntent(deck.id)) else startActivity(Intent(this, GameActivity::class.java).putExtra("reviewDeck", deck.id).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }.onFailure { Ui.toast(this, "Open AnkiDroid once first") } }.onFailure { Ui.toast(this, "Couldn't select this deck") } }
        }
    }
}
