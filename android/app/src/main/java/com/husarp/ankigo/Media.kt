package com.husarp.ankigo

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Finds AnkiDroid media files. The API gives only file NAMES (ReviewInfo.media_files and the
 * <img src>/[sound:] in the card HTML); it has no way to read media. So we try, in order:
 *  1. direct file paths (public /AnkiDroid folder; needs "All files access" on Android 11+),
 *     plus Android/data/<pkg>/... (normally blocked on 11+ for other apps),
 *  2. a folder the user picked with the system folder picker (SAF tree), cached index name->uri,
 *  3. our own copy imported from an AnkiDroid .apkg export (legacy format).
 */
object Media {
    private var safIndex: Map<String, Uri>? = null
    private var safTreeLoaded: String? = null
    val lastSource = HashMap<String, String>()

    fun ownDir(ctx: Context) = File(ctx.filesDir, "media").apply { mkdirs() }

    fun candidateDirs(ctx: Context): List<File> {
        val ext = Environment.getExternalStorageDirectory()
        return listOf(
            File(ext, "AnkiDroid/collection.media"),
            File("/storage/emulated/0/AnkiDroid/collection.media"),
            File(ext, "Android/data/${Anki.pkg}/files/AnkiDroid/collection.media"),
            File("/sdcard/AnkiDroid/collection.media"),
        ).distinctBy { it.absolutePath }
    }

    fun allFilesAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    fun treeUri(ctx: Context): Uri? = ctx.getSharedPreferences("spike", 0).getString("mediaTree", null)?.let(Uri::parse)
    fun setTree(ctx: Context, uri: Uri) {
        ctx.getSharedPreferences("spike", 0).edit().putString("mediaTree", uri.toString()).apply()
        safIndex = null
    }

    private fun safIndex(ctx: Context): Map<String, Uri> {
        val tree = treeUri(ctx) ?: return emptyMap()
        if (safIndex != null && safTreeLoaded == tree.toString()) return safIndex!!
        val m = HashMap<String, Uri>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val t0 = System.currentTimeMillis()
            ctx.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) m[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
            Report.log("MEDIA", "SAF index: ${m.size} files in ${System.currentTimeMillis() - t0} ms ($tree)")
        } catch (e: Exception) {
            Report.fail("MEDIA", "SAF index", e)
        }
        safIndex = m; safTreeLoaded = tree.toString()
        return m
    }

    /** A playable/readable location: either a File or a content Uri. */
    data class Found(val file: File?, val uri: Uri?, val source: String)

    fun find(ctx: Context, rawName: String): Found? {
        val name = Uri.decode(rawName)
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name == "." || name == "..") return null
        for (d in candidateDirs(ctx)) {
            val f = File(d, name)
            try {
                if (f.canRead()) return Found(f, null, "direct:${d.absolutePath}").also { lastSource[name] = it.source }
            } catch (_: Exception) {
            }
        }
        safIndex(ctx)[name]?.let { return Found(null, it, "saf").also { f -> lastSource[name] = f.source } }
        File(ownDir(ctx), name).let { if (it.canRead()) return Found(it, null, "imported").also { f -> lastSource[name] = f.source } }
        lastSource[name] = "NOT FOUND"
        return null
    }

    fun open(ctx: Context, name: String): InputStream? {
        val f = find(ctx, name) ?: return null
        return try {
            f.file?.inputStream() ?: ctx.contentResolver.openInputStream(f.uri!!)
        } catch (e: Exception) {
            Report.fail("MEDIA", "open $name (${f.source})", e); null
        }
    }

    fun probe(ctx: Context) {
        Report.log("MEDIA", "Android SDK ${Build.VERSION.SDK_INT}; All-files access=${allFilesAccess()}; SAF tree=${treeUri(ctx)}")
        for (d in candidateDirs(ctx)) {
            val list = try {
                d.list()
            } catch (e: Exception) {
                null
            }
            Report.log("MEDIA", "${d.absolutePath}: exists=${d.exists()} canRead=${d.canRead()} list=${list?.size ?: "null(blocked/none)"}")
        }
        Report.log("MEDIA", "imported copy: ${ownDir(ctx).list()?.size ?: 0} files")
        if (treeUri(ctx) != null) {
            safIndex = null
            safIndex(ctx)
        }
    }

    /** Imports media from a legacy .apkg/.colpkg (zip with JSON "media" map). */
    fun importApkg(ctx: Context, uri: Uri) {
        try {
            var map: JSONObject? = null
            val names = ArrayList<String>()
            // pass 1: read the media map
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        names.add(e.name)
                        if (e.name == "media") {
                            val bytes = z.readBytes()
                            map = try {
                                JSONObject(String(bytes))
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }
            }
            Report.log("MEDIA", "apkg entries: ${names.take(12)}${if (names.size > 12) " … (${names.size})" else ""}")
            val m = map
            if (m == null) {
                Report.fail("MEDIA", "apkg 'media' map is not JSON (new zstd/protobuf format). Re-export in AnkiDroid with 'Support older Anki versions' ticked.")
                return
            }
            var n = 0
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        val target = m.optString(e.name, "")
                        if (target.isNotEmpty()) {
                            if (target.contains('/') || target.contains('\\') || target == "." || target == "..") continue
                            File(ownDir(ctx), target).outputStream().use { z.copyTo(it) }
                            n++
                        }
                    }
                }
            }
            Report.ok("MEDIA", "imported $n media files from apkg")
        } catch (e: Exception) {
            Report.fail("MEDIA", "apkg import", e)
        }
    }
}
