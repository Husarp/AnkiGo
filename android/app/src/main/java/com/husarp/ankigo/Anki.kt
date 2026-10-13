package com.husarp.ankigo

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import org.json.JSONArray

/**
 * The one adapter for every AnkiDroid API call (FlashCardsContract via ContentResolver, raw URIs).
 * Works against any AnkiDroid package id: stable "com.ichi2.anki", GitHub Parallel builds
 * "com.ichi2.anki.A"/".B", debug "com.ichi2.anki.debug". Authority = "<pkg>.flashcards",
 * permission = "<pkg>.permission.READ_WRITE_DATABASE".
 */
object Anki {
    val KNOWN = listOf("com.ichi2.anki", "com.ichi2.anki.A", "com.ichi2.anki.B", "com.ichi2.anki.debug")

    data class Installed(val pkg: String, val versionName: String, val versionCode: Long, val providerSpec: Int?, val providerFound: Boolean)
    data class Deck(val id: Long, val name: String, val learn: Int, val review: Int, val new: Int, val dyn: Boolean) {
        val total get() = learn + review + new
    }
    data class Review(val nid: Long, val ord: Int, val buttons: Int, val nextTimes: List<String>, val media: List<String>)
    data class Note(val id: Long, val mid: Long, val fields: List<String>, val tags: String)
    data class Model(val id: Long, val name: String, val css: String, val fieldNames: List<String>, val type: Int)

    // Extended card columns (new in AnkiDroid 2.24+); old versions only know the default set.
    val CARD_STAT_COLS = arrayOf(
        "_id", "note_id", "ord", "card_name", "deck_id", "reps", "lapses", "type", "original_deck_id",
        "queue", "due", "original_due", "interval", "sm2_factor", "left", "original_position", "custom_data",
        "fsrs_stability", "fsrs_difficulty", "fsrs_desired_retention", "fsrs_decay", "last_review_time_secs", "flags",
    )
    val CARD_DEFAULT_COLS = arrayOf("note_id", "ord", "card_name", "deck_id", "question", "answer")

    var pkg: String = "com.ichi2.anki"
    val authority get() = "$pkg.flashcards"
    val permission get() = "$pkg.permission.READ_WRITE_DATABASE"
    fun uri(path: String): Uri = Uri.parse("content://$authority/$path")

    fun load(ctx: Context) {
        pkg = ctx.getSharedPreferences("spike", 0).getString("pkg", null)
            ?: installed(ctx).firstOrNull()?.pkg ?: "com.ichi2.anki"
    }

    fun select(ctx: Context, p: String) {
        pkg = p
        ctx.getSharedPreferences("spike", 0).edit().putString("pkg", p).apply()
    }

    fun installed(ctx: Context): List<Installed> = KNOWN.mapNotNull { p ->
        try {
            val pi = ctx.packageManager.getPackageInfo(p, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
            val prov = ctx.packageManager.resolveContentProvider("$p.flashcards", PackageManager.GET_META_DATA)
            Installed(p, pi.versionName ?: "?", code, prov?.metaData?.getInt("com.ichi2.anki.provider.spec", -1), prov != null)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun hasPermission(ctx: Context) = ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun Cursor.str(c: String): String? = getColumnIndex(c).let { if (it < 0 || isNull(it)) null else getString(it) }
    private fun Cursor.lng(c: String): Long = getColumnIndex(c).let { if (it < 0 || isNull(it)) 0L else getLong(it) }

    /** Reads every row as column->string map. */
    fun rows(c: Cursor?): List<Map<String, String?>> {
        if (c == null) return emptyList()
        c.use {
            val out = ArrayList<Map<String, String?>>()
            while (it.moveToNext()) {
                val m = LinkedHashMap<String, String?>()
                for (i in 0 until it.columnCount) m[it.getColumnName(i)] = if (it.isNull(i)) null else try {
                    it.getString(i)
                } catch (e: Exception) {
                    "<${e.javaClass.simpleName}>"
                }
                out.add(m)
            }
            return out
        }
    }

    fun decks(ctx: Context): List<Deck> {
        val c = ctx.contentResolver.query(uri("decks"), null, null, null, null) ?: return emptyList()
        val out = ArrayList<Deck>()
        c.use {
            while (it.moveToNext()) {
                val counts = it.str("deck_count")?.let { s -> JSONArray(s) }
                out.add(
                    Deck(
                        it.lng("deck_id"), it.str("deck_name") ?: "?",
                        counts?.optInt(0) ?: -1, counts?.optInt(1) ?: -1, counts?.optInt(2) ?: -1,
                        it.str("deck_dyn") == "1" || it.str("deck_dyn") == "true",
                    ),
                )
            }
        }
        return out
    }

    fun selectDeck(ctx: Context, deckId: Long): Int {
        val v = ContentValues(); v.put("deck_id", deckId)
        return ctx.contentResolver.update(uri("selected_deck"), v, null, null)
    }

    fun selectedDeck(ctx: Context): List<Map<String, String?>> = rows(ctx.contentResolver.query(uri("selected_deck"), null, null, null, null))

    fun nextCards(ctx: Context, deckId: Long, limit: Int = 1): List<Review> {
        val c = ctx.contentResolver.query(uri("schedule"), null, "limit=?, deckID=?", arrayOf(limit.toString(), deckId.toString()), null)
            ?: return emptyList()
        val out = ArrayList<Review>()
        c.use {
            while (it.moveToNext()) {
                val times = it.str("next_review_times")?.let { s -> JSONArray(s) }
                val media = it.str("media_files")?.let { s -> JSONArray(s) }
                out.add(
                    Review(
                        it.lng("note_id"), it.lng("ord").toInt(), it.lng("button_count").toInt(),
                        (0 until (times?.length() ?: 0)).map { i -> times!!.optString(i) },
                        (0 until (media?.length() ?: 0)).map { i -> media!!.optString(i) },
                    ),
                )
            }
        }
        return out
    }

    /** question/answer HTML (+ whatever extra columns this version knows). */
    fun card(ctx: Context, nid: Long, ord: Int, cols: Array<String> = CARD_DEFAULT_COLS): Map<String, String?>? =
        rows(ctx.contentResolver.query(uri("notes/$nid/cards/$ord"), cols, null, null, null)).firstOrNull()

    /** Extended stats columns if supported, else null + logs which failed. */
    fun cardStats(ctx: Context, nid: Long, ord: Int): Map<String, String?>? = try {
        card(ctx, nid, ord, if (flagsSupported(ctx)) CARD_STAT_COLS else CARD_STAT_COLS.filter { it != "flags" }.toTypedArray())
    } catch (e: Exception) {
        Report.fail("API", "extended card columns not supported", e); null
    }

    fun note(ctx: Context, nid: Long): Note? {
        val c = ctx.contentResolver.query(uri("notes/$nid"), null, null, null, null) ?: return null
        c.use {
            if (!it.moveToFirst()) return null
            return Note(nid, it.lng("mid"), (it.str("flds") ?: "").split('\u001f'), it.str("tags") ?: "")
        }
    }

    private val modelCache = HashMap<String, Model>()
    fun model(ctx: Context, mid: Long): Model? {
        modelCache["$pkg/$mid"]?.let { return it }
        val c = ctx.contentResolver.query(uri("models/$mid"), null, null, null, null) ?: return null
        c.use {
            if (!it.moveToFirst()) return null
            val names = (it.str("field_names") ?: "").let { s ->
                try {
                    JSONArray(s).let { a -> (0 until a.length()).map { i -> a.getString(i) } }
                } catch (_: Exception) {
                    s.split('\u001f')
                }
            }
            val m = Model(mid, it.str("name") ?: "?", it.str("css") ?: "", names, it.lng("type").toInt())
            modelCache["$pkg/$mid"] = m
            return m
        }
    }

    /** ease 1..4. Returns update count (AnkiDroid swallows errors and still returns 1, so callers verify). */
    fun answer(ctx: Context, nid: Long, ord: Int, ease: Int, timeTakenMs: Long, deckId: Long = 0): Int {
        // Known trap (AnkiWatch, AnkiDroid #18270): the schedule query only selects the deck temporarily,
        // so keep it selected and warm up the queue, and only answer the card that is on top of it.
        if (deckId != 0L) {
            selectDeck(ctx, deckId)
            val top = nextCards(ctx, deckId, 1).firstOrNull()
            if (top == null || top.nid != nid || top.ord != ord) {
                Report.fail("C2", "answer nid=$nid ord=$ord is not the top of the queue (top=${top?.nid}/${top?.ord}); skipped")
                return 0
            }
        }
        val v = ContentValues()
        v.put("note_id", nid); v.put("ord", ord); v.put("answer_ease", ease); v.put("time_taken", timeTakenMs)
        return ctx.contentResolver.update(uri("schedule"), v, null, null)
    }

    fun bury(ctx: Context, nid: Long, ord: Int): Int {
        val v = ContentValues(); v.put("note_id", nid); v.put("ord", ord); v.put("buried", 1)
        return ctx.contentResolver.update(uri("schedule"), v, null, null)
    }

    fun suspend(ctx: Context, nid: Long, ord: Int): Int {
        val v = ContentValues(); v.put("note_id", nid); v.put("ord", ord); v.put("suspended", 1)
        return ctx.contentResolver.update(uri("schedule"), v, null, null)
    }

    /** Deletes the whole note (all its cards) — the API has no single-card delete. */
    fun deleteNote(ctx: Context, nid: Long): Int = ctx.contentResolver.delete(uri("notes/$nid"), null, null)

    fun searchCards(ctx: Context, query: String, cols: Array<String>): List<Map<String, String?>> =
        rows(ctx.contentResolver.query(uri("cards"), cols, query, null, null))

    fun cardById(ctx: Context, id: Long): Map<String, String?>? =
        rows(ctx.contentResolver.query(uri("cards/$id"), CardState.COLS, null, null, null)).firstOrNull()

    fun deckOptions(ctx: Context, id: Long): String =
        rows(ctx.contentResolver.query(uri("decks/$id"), arrayOf("deck_id", "options"), null, null, null)).firstOrNull()?.get("options") ?: "{}"

    fun studySupported(ctx: Context): Boolean {
        val version = installed(ctx).firstOrNull { it.pkg == pkg } ?: return false
        return version.providerFound && (version.providerSpec == null || version.providerSpec == 2) &&
            com.husarp.ankigo.update.UpdateLogic.compare(version.versionName, "2.24") >= 0
    }
    fun flagsSupported(ctx: Context) = installed(ctx).firstOrNull { it.pkg == pkg }?.let {
        com.husarp.ankigo.update.UpdateLogic.compare(it.versionName, "2.25") >= 0
    } ?: false
    fun flag(ctx: Context, nid: Long, ord: Int, value: Int): Boolean {
        val v = ContentValues().apply { put("flags", value) }
        ctx.contentResolver.update(uri("notes/$nid/cards/$ord"), v, null, null)
        return cardStats(ctx, nid, ord)?.get("flags")?.toIntOrNull() == value
    }
    fun reviewIntent(deckId: Long) = android.content.Intent("android.intent.action.VIEW").apply {
        component = android.content.ComponentName(pkg, "com.ichi2.anki.IntentHandler")
        putExtra("EXTRA_DECK_ID", deckId)
    }
    fun editIntent(nid: Long) = android.content.Intent(android.content.Intent.ACTION_VIEW,
        Uri.parse("anki://x-callback-url/browser?search=nid:$nid")).setPackage(pkg)
    fun syncIntent() = android.content.Intent("com.ichi2.anki.DO_SYNC").setComponent(android.content.ComponentName(pkg, "com.ichi2.anki.IntentHandler"))
    fun launchIntent(ctx: Context) = ctx.packageManager.getLaunchIntentForPackage(pkg)

    /**
     * UNOFFICIAL probe: notes_v2 passes `selection` straight into SQL, so a UNION can read the revlog.
     * Not a contract; may be closed any time. Only used to learn what is reachable for C6.
     */
    fun revlogProbe(ctx: Context, sqlTail: String): List<Map<String, String?>> {
        val proj = arrayOf("_id", "guid", "mid", "mod", "usn", "tags", "flds", "sfld", "csum", "flags", "data")
        return rows(ctx.contentResolver.query(uri("notes_v2"), proj, "0 UNION $sqlTail", null, null))
    }

    fun reviewsTodayViaRevlog(ctx: Context): Int? = try {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 4); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
            if (timeInMillis > System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val r = revlogProbe(ctx, "SELECT count(), 0,0,0,0,0,0,0,0,0,0 FROM revlog WHERE id > ${cal.timeInMillis}")
        r.firstOrNull()?.get("_id")?.toIntOrNull()
    } catch (_: Exception) {
        null
    }
}
