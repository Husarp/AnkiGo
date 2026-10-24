package com.husarp.ankigo.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

// R18-E item 232 (the chart remembers its choice) and 233 (six charts removed). Pure rules, no Android.
class R18EChartPrefsTest {
    private fun dec(m: String? = null, p: String? = null, d: String? = null, g: String? = null) = ChartPrefs.decode(m, p, d, g)

    @Test fun nothingSavedIsTheDefault() {
        assertEquals(ChartPrefs.Choice(ChartMetric.REVIEWS, ChartPeriod.D30, null, null), dec())
    }

    @Test fun aSavedChoiceComesBackWhole() {
        val c = dec("XP", "D90", "My deck", "WEEK")
        assertEquals(ChartPrefs.Choice(ChartMetric.XP, ChartPeriod.D90, "My deck", ChartGrain.WEEK), c)
        // every field survives a name round trip
        val back = dec(c.metric.name, c.period.name, c.deck, c.grain?.name)
        assertEquals(c, back)
    }

    @Test fun anUnknownOrRemovedMetricFallsBackToReviews() {
        for (n in listOf("HEALING", "MONEY_SPENT", "DMG_DEALT", "DMG_TAKEN", "DMG_BLOCKED", "MISSES", "NONSENSE", "")) {
            assertEquals(n, ChartMetric.REVIEWS, dec(m = n, p = "D90").metric)
        }
    }

    @Test fun answersIsNotInTheListSoItFallsBackToo() {
        assertEquals(ChartMetric.REVIEWS, dec(m = "ANSWERS").metric)
    }

    @Test fun aPeriodTheMetricDoesNotOfferIsThirtyDays() {
        assertEquals(ChartPeriod.D30, dec(m = "DUE", p = "D365").period)
        assertEquals(ChartPeriod.D7, dec(m = "DUE", p = "D7").period)
        assertEquals(ChartPeriod.D30, dec(p = "bogus").period)
    }

    @Test fun aGrainThatDoesNotFitThePeriodIsDroppedToTheDefault() {
        assertNull(dec(p = "D7", g = "WEEK").grain)       // 7 days only knows days
        assertNull(dec(p = "D30", g = "MONTH").grain)
        assertNull(dec(p = "D90", g = "bogus").grain)
        assertEquals(ChartGrain.MONTH, dec(p = "D365", g = "MONTH").grain)
        assertNull(dec(m = "CARD_STATES", g = "DAY").grain) // a picture of now has no bar size
    }

    @Test fun aMissingDeckFallsBackToAllDecksOnceTheListIsLoaded() {
        val c = dec(d = "Gone deck")
        assertEquals("Gone deck", c.deck)                                          // not loaded yet: kept
        assertEquals("Gone deck", ChartPrefs.withKnownDeck(c, emptyList()).deck)
        assertNull(ChartPrefs.withKnownDeck(c, listOf("A", "B")).deck)             // loaded, not there
        assertEquals("A", ChartPrefs.withKnownDeck(dec(d = "A"), listOf("A", "B")).deck)
        assertNull(ChartPrefs.withKnownDeck(dec(), listOf("A")).deck)
        assertNull(dec(d = "").deck)
    }

    @Test fun theRemovedChartsAreNotInTheShowList() {
        val listed = ChartMetric.menu.flatMap { it.second }.map { it.name }
        for (n in listOf("HEALING", "MONEY_SPENT", "DMG_DEALT", "DMG_TAKEN", "DMG_BLOCKED", "MISSES")) assertFalse(n, n in listed)
        assertEquals(listOf("MONEY", "CATCHES", "WINS", "XP"), ChartMetric.menu.first { it.first == ChartGroup.GAME }.second.map { it.name })
    }
}
