package com.husarp.ankigo.data

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.sound.CryUrls
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** R8-01: the assets mirror (key rule, index loading, pack reads, fallbacks) with a fake host, and Http.getRange against a local server. */
class MirrorTest {
    private val spriteUrl = "https://raw.githubusercontent.com/x/y/c1/sprites/a.png"
    private val cryUrl = "https://play.pokemonshowdown.com/audio/z.ogg"
    private val fileA = ByteArray(300) { (it * 7).toByte() }
    private val fileB = ByteArray(5) { (it + 40).toByte() }

    private class Built(val zip: ByteArray, val files: List<Row>)
    private class Row(val key: String, val offset: Long, val length: Int, val crc: Long)

    /** A STORED zip of [entries], with the offset of each file's bytes found by walking the local headers (like the builder does). */
    private fun buildZip(entries: List<Pair<String, ByteArray>>): Built {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, b) in entries) {
                val e = ZipEntry(name); e.method = ZipEntry.STORED; e.size = b.size.toLong(); e.compressedSize = b.size.toLong()
                e.crc = CRC32().also { it.update(b) }.value; e.time = 0
                z.putNextEntry(e); z.write(b); z.closeEntry()
            }
        }
        val zip = bos.toByteArray()
        val bb = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 0
        val rows = ArrayList<Row>()
        for ((name, b) in entries) {
            assertEquals(0x04034B50, bb.getInt(pos))
            val n = bb.getShort(pos + 26).toInt(); val m = bb.getShort(pos + 28).toInt()
            val off = pos + 30 + n + m
            assertArrayEquals(b, zip.copyOfRange(off, off + b.size))
            rows += Row(name, off.toLong(), b.size, CRC32().also { it.update(b) }.value)
            pos = off + b.size
        }
        return Built(zip, rows)
    }

    private fun indexJson(format: Int = 1, tag: String = Mirror.TAG, rows: List<Row>, pack: String = "pack-t.zip", packOf: Int = 0): ByteArray {
        val o = JSONObject()
        o.put("format", format); o.put("tag", tag)
        o.put("packs", JSONArray().put(JSONObject().put("name", pack).put("size", 1).put("sha256", "00").put("files", rows.size)))
        val files = JSONArray()
        for (r in rows) files.put(JSONArray().put(r.key).put(packOf).put(r.offset).put(r.length).put(r.crc))
        o.put("files", files); o.put("own", JSONArray())
        return o.toString().toByteArray()
    }

    private fun gz(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    private class Fake(val pack: ByteArray?, var index: ByteArray?) : MirrorHost {
        val gets: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
        val ranges: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
        var originals = HashMap<String, ByteArray>()
        var indexError: IOException? = null
        var rangeError: IOException? = null
        var rangeNull = false
        var corrupt = false
        override fun get(url: String, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray? {
            gets += url
            if (url == Mirror.BASE + "index.json.gz") { indexError?.let { throw it }; return index }
            return originals[url]
        }
        override fun range(url: String, offset: Long, length: Int, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray? {
            ranges += "$url@$offset+$length"
            rangeError?.let { throw it }
            if (rangeNull || pack == null) return null
            val b = pack.copyOfRange(offset.toInt(), minOf(pack.size, offset.toInt() + length))
            if (corrupt) b[0] = (b[0] + 1).toByte()
            if (progress != null && b.isNotEmpty()) { // like Http.getRange: chunk by chunk, ending on (length, length)
                var got = 0
                while (got < b.size) { got = minOf(b.size, got + 100); progress(got.toLong(), b.size.toLong()) }
            }
            return b
        }
        fun indexGets() = gets.count { it.endsWith("index.json.gz") }
        fun originalGets() = gets.filter { !it.endsWith("index.json.gz") }
    }

    private lateinit var dir: File
    private var now = 1_000L
    private val logs = ArrayList<String>()
    private lateinit var built: Built
    private lateinit var fake: Fake

    @Before fun setUp() {
        dir = Files.createTempDirectory("ankigo-mirror").toFile()
        built = buildZip(listOf("gh/x/y@c1/sprites/a.png" to fileA, "showdown/audio/z.ogg" to fileB))
        fake = Fake(built.zip, gz(indexJson(rows = built.files)))
        fake.originals[spriteUrl] = byteArrayOf(9, 9)
        fake.originals[cryUrl] = byteArrayOf(8, 8, 8)
        Mirror.resetForTest()
        Mirror.host = fake; Mirror.clock = { now }; Mirror.logSink = { logs += it }
    }

    @After fun tearDown() { dir.deleteRecursively(); Mirror.resetForTest() }

    // ---- keys ----

    @Test fun rawAndJsDelivrShareOneKeyAndShowdownHasItsOwn() {
        assertEquals("gh/x/y@c1/sprites/a.png", Mirror.keyFor(spriteUrl))
        assertEquals("gh/x/y@c1/sprites/a.png", Mirror.keyFor("https://cdn.jsdelivr.net/gh/x/y@c1/sprites/a.png"))
        assertEquals("showdown/sprites/gen5ani/foo.gif", Mirror.keyFor("https://play.pokemonshowdown.com/sprites/gen5ani/foo.gif"))
        assertNull(Mirror.keyFor("https://example.com/a.png"))
        assertNull(Mirror.keyFor("https://github.com/Husarp/AnkiGo/releases/download/assets-1/bg-reef.png"))
        assertNull(Mirror.keyFor("https://raw.githubusercontent.com/x/y/c1"))
        assertNull(Mirror.keyFor("https://cdn.jsdelivr.net/gh/x/y/readme.md"))
        assertNull(Mirror.keyFor("https://play.pokemonshowdown.com/"))
        assertNull(Mirror.keyFor("not a url"))
    }

    @Test fun everyUrlTheAppBuildsHasAKeyAndTheChainsAgree() {
        val sp = TestData.species(7, "testmon")
        val urls = ArrayList<String>()
        for (kind in SpriteKind.values()) for (shiny in listOf(false, true)) urls += SpriteUrls.battle(sp, kind, shiny)
        urls += SpriteUrls.battle(TestData.species(700, "testmon2"), SpriteKind.FRONT, true)
        urls += SpriteUrls.staticChain(7, false) + SpriteUrls.staticChain(7, true) + SpriteUrls.static(7, false)
        urls += SpriteUrls.item("test-item") + CryUrls.chain(7) + CryUrls.chain(10091) + DataStore.SOURCES.values + Backdrops.url("forest")
        for (u in urls) assertNotNull("no key for $u", Mirror.keyFor(u))
        val static = SpriteUrls.staticChain(7, false)
        assertEquals(Mirror.keyFor(static[0]), Mirror.keyFor(static[1]))
        val cry = CryUrls.chain(7)
        assertEquals(Mirror.keyFor(cry[0]), Mirror.keyFor(cry[1]))
        assertEquals("gh/PokeAPI/cries@${CryUrls.COMMIT}/cries/pokemon/latest/7.ogg", Mirror.keyFor(cry[0]))
        assertEquals("showdown/data/pokedex.json", Mirror.keyFor(DataStore.SOURCES.getValue("pokedex.json")))
        assertEquals("showdown/fx/bg-forest.png", Mirror.keyFor(Backdrops.url("forest")))
        assertEquals("gh/PokeAPI/sprites@${DataStore.SPRITES_COMMIT}/sprites/items/test-item.png", Mirror.keyFor(SpriteUrls.item("test-item")))
    }

    // ---- pack reads ----

    @Test fun aMirrorHitReturnsTheExactBytesAndAsksTheOriginalNothing() {
        val seen = ArrayList<Pair<Long, Long>>()
        assertArrayEquals(fileA, Mirror.get(spriteUrl, 5_000) { a, b -> seen += a to b })
        assertArrayEquals(fileB, Mirror.get(cryUrl))
        assertEquals(emptyList<String>(), fake.originalGets())
        assertEquals(2, fake.ranges.size)
        assertTrue(fake.ranges[0].startsWith(Mirror.BASE + "pack-t.zip@"))
        assertEquals(listOf(100L to 300L, 200L to 300L, 300L to 300L), seen)
        assertEquals(1, fake.indexGets())
        assertEquals(listOf("index assets-1: 2 files"), logs)
    }

    @Test fun jsDelivrUrlIsServedFromTheSameMirrorFile() {
        assertArrayEquals(fileA, Mirror.get("https://cdn.jsdelivr.net/gh/x/y@c1/sprites/a.png"))
        assertEquals(emptyList<String>(), fake.originalGets())
    }

    @Test fun aUrlNotInTheIndexOrNotMirroredGoesToTheOriginal() {
        fake.originals["https://raw.githubusercontent.com/x/y/c1/other.png"] = byteArrayOf(5)
        fake.originals["https://example.com/q"] = byteArrayOf(6)
        assertArrayEquals(byteArrayOf(5), Mirror.get("https://raw.githubusercontent.com/x/y/c1/other.png"))
        assertArrayEquals(byteArrayOf(6), Mirror.get("https://example.com/q"))
        assertNull(Mirror.get("https://raw.githubusercontent.com/x/y/c1/missing.png"))
        assertEquals(0, fake.ranges.size)
    }

    @Test fun anUnmirroredUrlNeverLoadsTheIndex() {
        Mirror.get("https://example.com/q")
        assertEquals(0, fake.indexGets())
    }

    @Test fun crcMismatchGoesToTheOriginal() {
        fake.corrupt = true
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        assertEquals(listOf(spriteUrl), fake.originalGets())
        assertEquals(1, logs.count { it.startsWith("fallback") })
    }

    @Test fun shortReadGoesToTheOriginal() {
        val short = buildZip(listOf("gh/x/y@c1/sprites/a.png" to fileA))
        // the index promises 300 bytes at an offset that runs past the end of the pack
        val rows = listOf(Row("gh/x/y@c1/sprites/a.png", (short.zip.size - 10).toLong(), 300, short.files[0].crc))
        fake.index = indexJson(rows = rows)
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        assertEquals(1, fake.originalGets().size)
    }

    @Test fun rangeRefusedOrIoErrorOrMissingPackGoesToTheOriginal() {
        fake.rangeNull = true
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        fake.rangeNull = false; fake.rangeError = IOException("HTTP 200 for range")
        assertArrayEquals(byteArrayOf(8, 8, 8), Mirror.get(cryUrl))
        assertEquals(2, fake.originalGets().size)
        assertEquals("only the first reason is logged", 1, logs.count { it.startsWith("fallback") })
    }

    @Test fun anIoErrorFromTheOriginalStillPropagates() {
        fake.rangeError = IOException("x")
        val failing = object : MirrorHost by fake {
            override fun get(url: String, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray? =
                if (url.endsWith("index.json.gz")) fake.get(url, timeoutMs, progress) else throw IOException("original down")
        }
        Mirror.host = failing
        try { Mirror.get(spriteUrl); fail() } catch (e: IOException) { assertEquals("original down", e.message) }
    }

    @Test fun aCrcRejectedReadStreamsTheOriginalsProgressFromZeroAgain() {
        fake.corrupt = true
        val seen = ArrayList<Pair<Long, Long>>()
        val streaming = object : MirrorHost by fake {
            override fun get(url: String, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray? {
                val b = fake.get(url, timeoutMs, progress)
                if (!url.endsWith("index.json.gz")) progress?.invoke(1, 2)
                return b
            }
        }
        Mirror.host = streaming
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl, 5_000) { a, b -> seen += a to b })
        assertEquals(listOf(100L to 300L, 200L to 300L, 300L to 300L, 1L to 2L), seen)
    }

    // ---- pack backoff ----

    private fun threeFailures(): Int {
        var n = 0
        repeat(3) { assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl)); n++ }
        return n
    }

    @Test fun threePackFailuresInARowSwitchTheMirrorOffForTenMinutes() {
        fake.rangeError = IOException("HTTP 503 for range")
        threeFailures()
        assertEquals(3, fake.ranges.size)
        assertEquals(1, logs.count { it.startsWith("pack reads failed 3 times") })
        // cooldown: straight to the original, no pack request, not even for a different file
        fake.rangeError = null
        repeat(4) { assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl)) }
        assertArrayEquals(byteArrayOf(8, 8, 8), Mirror.get(cryUrl))
        assertEquals(3, fake.ranges.size)
        now += 9 * 60_000L
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        assertEquals(3, fake.ranges.size)
        // after the ten minutes the mirror is tried again, and works
        now += 61_000L
        assertArrayEquals(fileA, Mirror.get(spriteUrl))
        assertEquals(4, fake.ranges.size)
        assertEquals(1, logs.count { it.startsWith("pack reads failed") })
    }

    @Test fun aMissingPackCountsAsTransportTroubleToo() {
        fake.rangeNull = true
        threeFailures()
        fake.rangeNull = false
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        assertEquals(3, fake.ranges.size)
    }

    @Test fun aSuccessResetsTheFailureCount() {
        fake.rangeError = IOException("x")
        repeat(2) { Mirror.get(spriteUrl) }
        fake.rangeError = null
        assertArrayEquals(fileA, Mirror.get(spriteUrl))
        fake.rangeError = IOException("x")
        repeat(2) { Mirror.get(spriteUrl) }
        fake.rangeError = null
        assertArrayEquals("2 + 2 failures with a hit between never trip it", fileA, Mirror.get(spriteUrl))
        assertEquals(0, logs.count { it.startsWith("pack reads failed") })
    }

    @Test fun crcMismatchesNeverTripTheBackoff() {
        fake.corrupt = true
        repeat(5) { assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl)) }
        assertEquals(5, fake.ranges.size)
        fake.corrupt = false
        assertArrayEquals(fileA, Mirror.get(spriteUrl))
        assertEquals(0, logs.count { it.startsWith("pack reads failed") })
    }

    @Test fun failuresAfterTheCooldownNeedThreeMoreToTripAgain() {
        fake.rangeError = IOException("x")
        threeFailures()
        now += 10 * 60_000L + 1
        repeat(2) { Mirror.get(spriteUrl) }
        assertEquals(5, fake.ranges.size)
        Mirror.get(spriteUrl)
        assertEquals(6, fake.ranges.size)
        Mirror.get(spriteUrl)
        assertEquals("tripped again", 6, fake.ranges.size)
        assertEquals(2, logs.count { it.startsWith("pack reads failed") })
    }

    // ---- the index ----

    @Test fun anUnreachableIndexMeansOriginalsAndNoNewTryForTenMinutes() {
        fake.indexError = IOException("offline")
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        now += 9 * 60_000L
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        assertEquals(1, fake.indexGets())
        assertEquals(0, fake.ranges.size)
        assertEquals(1, logs.size)
        assertTrue(logs[0], logs[0].startsWith("index unreachable: IOException: offline"))
        now += 61_000L
        fake.indexError = null
        assertArrayEquals(fileA, Mirror.get(spriteUrl))
        assertEquals(2, fake.indexGets())
    }

    @Test fun anIndexThatIsNotFoundCountsAsAFailure() {
        fake.index = null
        assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
        Mirror.get(spriteUrl)
        assertEquals(1, fake.indexGets())
    }

    @Test fun aPlainJsonIndexLoadsToo() {
        fake.index = indexJson(rows = built.files)
        assertArrayEquals(fileA, Mirror.get(spriteUrl))
    }

    @Test fun wrongTagOrFormatOrGarbageIsIgnored() {
        for (bad in listOf(indexJson(tag = "assets-2", rows = built.files), indexJson(format = 2, rows = built.files), "nonsense".toByteArray(), gz("{".toByteArray()))) {
            Mirror.resetForTest(); Mirror.host = fake; Mirror.clock = { now }; Mirror.logSink = { logs += it }
            fake.index = bad
            assertArrayEquals(byteArrayOf(9, 9), Mirror.get(spriteUrl))
            assertEquals(0, fake.ranges.size)
        }
    }

    @Test fun theIndexIsCachedAsPlainJsonAndReadBackWithoutTheNetwork() {
        Mirror.cacheDir = dir
        assertArrayEquals(fileA, Mirror.get(spriteUrl))
        val cache = File(dir, "assets-1-index.json")
        assertTrue(cache.isFile)
        assertEquals('{', cache.readBytes()[0].toInt().toChar())
        assertEquals(1, fake.indexGets())
        // a new process: same folder, nothing else known
        Mirror.resetForTest(); Mirror.host = fake; Mirror.clock = { now }; Mirror.logSink = { logs += it }; Mirror.cacheDir = dir
        assertArrayEquals(fileB, Mirror.get(cryUrl))
        assertEquals("no second index download", 1, fake.indexGets())
    }

    @Test fun aBrokenCacheFileIsReplacedByADownload() {
        Mirror.cacheDir = dir
        File(dir, "assets-1-index.json").writeText("{broken")
        assertArrayEquals(fileA, Mirror.get(spriteUrl))
        assertEquals(1, fake.indexGets())
        assertTrue(File(dir, "assets-1-index.json").readText().let { it.startsWith("{") && it.contains("\"format\":1") })
    }

    @Test fun withoutAFolderTheIndexStaysInMemory() {
        Mirror.get(spriteUrl); Mirror.get(cryUrl)
        assertEquals(1, fake.indexGets())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test fun parallelCallsLoadTheIndexOnce() {
        val threads = List(6) { Thread { Mirror.get(spriteUrl) } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(1, fake.indexGets())
        assertEquals(6, fake.ranges.size)
    }

    @Test fun indexEntriesWithBadNumbersAreSkipped() {
        val o = JSONObject(String(indexJson(rows = built.files)))
        o.getJSONArray("files").put(JSONArray().put("gh/x/y@c1/bad.png").put(7).put(0).put(5).put(1))
        o.getJSONArray("files").put(JSONArray().put("gh/x/y@c1/bad2.png").put(0).put(-1).put(5).put(1))
        fake.index = o.toString().toByteArray()
        Mirror.get(spriteUrl)
        assertEquals(listOf("index assets-1: 2 files"), logs)
    }

    // ---- Http.getRange against a local server ----

    /** A request seen by [LocalServer]: path and headers (lower-case names). */
    private class Req(val path: String, val headers: Map<String, String>)

    /** A tiny one-thread-per-connection HTTP/1.0 server on 127.0.0.1: [handler] returns the whole raw response. */
    private class LocalServer(val handler: (Req) -> ByteArray) {
        val socket = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        val base = "http://127.0.0.1:${socket.localPort}"
        init {
            Thread {
                try {
                    while (true) {
                        val c = socket.accept()
                        Thread {
                            c.use {
                                val r = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                                val path = (r.readLine() ?: return@Thread).split(' ')[1]
                                val h = HashMap<String, String>()
                                while (true) { val l = r.readLine(); if (l.isNullOrEmpty()) break; h[l.substringBefore(':').lowercase()] = l.substringAfter(':').trim() }
                                it.getOutputStream().use { out -> out.write(handler(Req(path, h))); out.flush() }
                            }
                        }.start()
                    }
                } catch (_: IOException) { }
            }.also { it.isDaemon = true }.start()
        }
        fun stop() = socket.close()
    }

    private fun resp(code: Int, headers: Map<String, String> = emptyMap(), body: ByteArray = ByteArray(0)): ByteArray {
        val head = StringBuilder("HTTP/1.0 $code X\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
        for ((k, v) in headers) head.append("$k: $v\r\n")
        head.append("\r\n")
        return head.toString().toByteArray(Charsets.ISO_8859_1) + body
    }

    private val data = ByteArray(1000) { (it % 251).toByte() }

    private fun withServer(handler: (Req) -> ByteArray, body: (String) -> Unit) {
        val s = LocalServer(handler)
        try { body(s.base) } finally { s.stop() }
    }

    @Test fun getRangeFollowsARedirectKeepingTheRangeHeader() {
        val seen = java.util.Collections.synchronizedList(ArrayList<String>())
        withServer({ r ->
            seen += r.path + ":" + r.headers["range"] + ":" + r.headers["accept-encoding"]
            if (r.path == "/a") resp(302, mapOf("Location" to "/b"))
            else {
                val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(r.headers.getValue("range"))!!
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                resp(206, mapOf("Content-Range" to "bytes $a-$b/${data.size}"), data.copyOfRange(a, b + 1))
            }
        }) { base ->
            assertArrayEquals(data.copyOfRange(100, 140), Http.getRange("$base/a", 100, 40))
        }
        assertEquals(listOf("/a:bytes=100-139:identity", "/b:bytes=100-139:identity"), seen)
    }

    @Test fun getRangeReportsProgressWhileReadingAMultiChunkBody() {
        val big = ByteArray(200_000) { (it % 253).toByte() }
        withServer({ r ->
            val m = Regex("bytes=(\\d+)-(\\d+)").matchEntire(r.headers.getValue("range"))!!
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            resp(206, mapOf("Content-Range" to "bytes $a-$b/${big.size}"), big.copyOfRange(a, b + 1))
        }) { base ->
            val seen = ArrayList<Pair<Long, Long>>()
            assertArrayEquals(big.copyOfRange(1000, 101_000), Http.getRange("$base/x", 1000, 100_000) { a, b -> seen += a to b })
            assertTrue("several calls: ${seen.size}", seen.size >= 3)
            assertTrue(seen.all { it.second == 100_000L })
            assertEquals(seen.map { it.first }.sorted(), seen.map { it.first })
            assertEquals(100_000L to 100_000L, seen.last())
        }
    }

    @Test fun getRangeRefusesA200AnswerAndAWrongContentRangeAndAShortBody() {
        withServer({ r ->
            when (r.path) {
                "/full" -> resp(200, body = data)
                "/wrong" -> resp(206, mapOf("Content-Range" to "bytes 0-9/1000"), data.copyOfRange(0, 10))
                "/short" -> resp(206, mapOf("Content-Range" to "bytes 5-14/1000"), data.copyOfRange(5, 9))
                "/err" -> resp(500)
                "/gone" -> resp(404)
                else -> resp(206, mapOf("Content-Range" to "bytes 5-14/1000"), data.copyOfRange(5, 15))
            }
        }) { base ->
            for (p in listOf("full", "wrong", "short", "err")) {
                try { Http.getRange("$base/$p", 5, 10); fail("$p should fail") } catch (_: IOException) { }
            }
            assertNull(Http.getRange("$base/gone", 5, 10))
            assertArrayEquals(data.copyOfRange(5, 15), Http.getRange("$base/ok", 5, 10))
        }
    }
}
