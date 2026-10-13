package com.husarp.ankigo

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app report log. Kept in a file so the activities and widgets share one log. */
object Report {
    private var file: File? = null
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    var listener: ((String) -> Unit)? = null

    fun init(ctx: Context) {
        if (file == null) file = File(ctx.applicationContext.filesDir, "report.txt")
    }

    @Synchronized
    fun log(tag: String, msg: String) {
        val line = "${fmt.format(Date())} [$tag] $msg"
        android.util.Log.i("AnkiGo", line)
        try {
            file?.appendText(line + "\n")
        } catch (_: Exception) {
        }
        listener?.invoke(line)
    }

    fun ok(tag: String, msg: String) = log(tag, "OK   $msg")
    fun fail(tag: String, msg: String) = log(tag, "FAIL $msg")
    fun fail(tag: String, msg: String, e: Throwable) =
        log(tag, "FAIL $msg -> ${e.javaClass.simpleName}: ${e.message}")

    fun all(): String = try {
        file?.readText() ?: ""
    } catch (_: Exception) {
        ""
    }

    fun clear() {
        try {
            file?.writeText("")
        } catch (_: Exception) {
        }
    }
}
