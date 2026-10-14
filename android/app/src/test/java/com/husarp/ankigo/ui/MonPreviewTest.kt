package com.husarp.ankigo.ui

import com.husarp.ankigo.study.StudyRules.SpriteSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MonPreviewTest {
    @Test fun cachedGifIsAnimatedEvenWithStaticPng() {
        assertEquals(SpriteSource.ANIMATED, MonPreview.source("25.gif", true))
        assertEquals(SpriteSource.ANIMATED, MonPreview.source("25s.GIF", false))
    }

    @Test fun cachedPngOrNothingFallsBackToStatic() {
        assertEquals(SpriteSource.STATIC, MonPreview.source("700.png", true))
        assertEquals(SpriteSource.STATIC, MonPreview.source(null, true))
    }

    @Test fun noSpriteAtAllIsPlaceholder() {
        assertEquals(SpriteSource.PLACEHOLDER, MonPreview.source(null, false))
        assertEquals(SpriteSource.PLACEHOLDER, MonPreview.source("700.png", false))
    }

    @Test fun frameDurationsHaveAFloor() {
        assertArrayEquals(intArrayOf(20, 100, 150), MonPreview.durations(intArrayOf(0, 100, 150), 3))
        assertArrayEquals(intArrayOf(80, 100), MonPreview.durations(intArrayOf(80), 2))
    }
}
