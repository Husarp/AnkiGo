package com.husarp.ankigo.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** R18 K (PLAN 268): no main source / resource file (Kotlin, XML, raw text) contains the rouble sign any more: coins show as the coin icon or the word. */
class R18KNoRoubleSignTest {
    @Test fun noMainFileHasARoubleSign() {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/main").isDirectory) dir = dir.parentFile
        val main = File(dir ?: error("src/main not found from ${File("").absolutePath}"), "src/main")
        val sign = "₽"
        val bad = main.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml", "txt", "json", "html", "js") }
            .filter { sign in it.readText() }.map { it.path }.toList()
        assertTrue("rouble sign in: $bad", bad.isEmpty())
    }
}
