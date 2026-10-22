package com.husarp.ankigo.sound

import java.io.File
import java.io.IOException

/** Where cries come from: PokeAPI/cries pinned to one commit (the same files through GitHub raw and jsDelivr). Pure. */
object CryUrls {
    const val COMMIT = "b3a9044bb839b5e3152aaa536c7b463a0fcf0140"

    /** [num] = dex number, or the form id for regional forms (e.g. 10091). GitHub raw first, jsDelivr as the fallback. */
    fun chain(num: Int) = listOf(
        "https://raw.githubusercontent.com/PokeAPI/cries/$COMMIT/cries/pokemon/latest/$num.ogg",
        "https://cdn.jsdelivr.net/gh/PokeAPI/cries@$COMMIT/cries/pokemon/latest/$num.ogg",
    )
}

/** One HTTP GET: body, null for "not found", IOException for trouble (same contract as Http.get). */
typealias CryGet = (url: String) -> ByteArray?

/**
 * Cry files in a folder (`{num}.ogg`; a few are MP3 data under that name, see [isMp3]), downloaded one at a time on demand. Plain java.io so it can be tested.
 * A "not found" answer from every URL is remembered for 7 days (`{num}.missing`); network errors leave no note.
 */
class CryStore(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    fun file(num: Int) = File(dir, "$num.ogg")

    /** The cached cry or null. */
    fun cached(num: Int): File? = file(num).takeIf { it.isFile && it.length() > 0 }

    private fun marker(num: Int) = File(dir, "$num.missing")

    /** True when a recent "not found" note says not to ask again yet. */
    fun knownMissing(num: Int): Boolean {
        val m = marker(num)
        return m.isFile && now() - m.lastModified() < RETRY_MISSING_MS
    }

    /** Blocking: the cached file, else downloads it. Null when there is none (or the network failed). */
    fun fetch(num: Int, get: CryGet): File? {
        cached(num)?.let { return it }
        if (knownMissing(num)) return null
        dir.mkdirs()
        var networkTrouble = false
        for (url in CryUrls.chain(num)) {
            val bytes = try { get(url) } catch (_: IOException) { networkTrouble = true; continue } ?: continue
            if (!isCry(bytes)) { networkTrouble = true; continue } // an error page is not "not found"
            val tmp = File(dir, "$num.ogg.${System.nanoTime()}.tmp")
            try {
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(file(num))) { tmp.delete(); networkTrouble = true; continue }
            } catch (_: IOException) {
                tmp.delete(); networkTrouble = true; continue
            }
            marker(num).delete()
            return file(num)
        }
        if (!networkTrouble) {
            try { marker(num).writeText(now().toString()); marker(num).setLastModified(now()) } catch (_: IOException) { }
        }
        return null
    }

    /** PLAN 167: the folder the cries are in ([DownloadAll] looks at it to see what is on the phone). */
    fun folder(): File = dir

    companion object {
        const val RETRY_MISSING_MS = 7L * 24 * 3600 * 1000

        /** Ogg files start with "OggS". */
        fun isOgg(b: ByteArray) = b.size > 4 && b[0] == 'O'.code.toByte() && b[1] == 'g'.code.toByte() && b[2] == 'g'.code.toByte() && b[3] == 'S'.code.toByte()

        /**
         * MP3 data: an ID3 tag, or an MPEG audio frame header (11 sync bits, a real version, layer III or II or I, a real bitrate and sample rate).
         * PokeAPI's cries of Pikachu (25), Meltan (808) and Melmetal (809) are MP3 data in `.ogg` files; Android plays them all the same
         * (it looks at the content, not the name).
         */
        fun isMp3(b: ByteArray): Boolean {
            if (b.size <= 4) return false
            if (b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) return true
            val h1 = b[0].toInt() and 0xFF; val h2 = b[1].toInt() and 0xFF; val h3 = b[2].toInt() and 0xFF
            return h1 == 0xFF && (h2 and 0xE0) == 0xE0 && ((h2 shr 3) and 3) != 1 && ((h2 shr 1) and 3) != 0 &&
                (h3 shr 4) != 0xF && ((h3 shr 2) and 3) != 3
        }

        /** A body that is a cry: Ogg, or MP3 data. Anything else (an HTML error page) is not saved. */
        fun isCry(b: ByteArray) = isOgg(b) || isMp3(b)
    }
}
