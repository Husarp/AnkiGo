package com.husarp.ankigo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** R4 fix X3-3: the Downloads page marks the pictures "Downloaded ✓" from the cache when every picture of the enabled generations is already there. */
class DownloadsAdoptTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun cache(vararg nums: Int): (Int) -> Boolean {
        val dir = tmp.newFolder("static")
        for (n in nums) File(dir, "$n.png").writeBytes(byteArrayOf(1))
        return { File(dir, "$it.png").isFile }
    }

    @Test fun everyPictureThereIsComplete() {
        assertTrue(DownloadsDone.picturesComplete(listOf(1, 2, 3), cache(1, 2, 3, 4)))
    }

    @Test fun oneMissingPictureIsNotComplete() {
        assertFalse(DownloadsDone.picturesComplete(listOf(1, 2, 3), cache(1, 3))) // strict: the green state disables the button
    }

    @Test fun nothingToCoverIsNotComplete() {
        assertFalse(DownloadsDone.picturesComplete(emptyList(), cache()))
    }

    @Test fun anEmptyCacheIsNotComplete() {
        assertFalse(DownloadsDone.picturesComplete(listOf(1), cache()))
    }
}
