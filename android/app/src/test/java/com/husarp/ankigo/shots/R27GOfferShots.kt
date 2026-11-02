package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class R27GOfferShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
        (if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList())

    @Test fun approvedOffersDayNightLightDark360() {
        for (night in listOf(false, true)) {
            paparazzi.shoot("g-a-${if (night) "night" else "day"}", listOf(Size.SMALL)) { ctx ->
                var tapped = 0
                val daily = ShopViews.dealCard(ctx, "super-potion", "Super Potion", ShopPlan.Price(490, 700),
                    "Ends in 2 h 53 min", "Super Potion, 490 instead of 700", "Restores 60 HP", night) { tapped++ }
                val weekend = ShopViews.weekendCard(ctx, ShopViews.itemArt(ctx, "lucky-egg", 48), "Lucky Egg",
                    "Ends in 1 d 5 h", ShopViews.priceNormal(ctx, 6000), "Lucky Egg, 6,000") { tapped++ }
                assertTrue(texts(daily).contains(if (night) "DEAL OF THE NIGHT" else "DEAL OF THE DAY"))
                assertTrue(texts(weekend).contains("Ends in 1 d 5 h"))
                daily.performClick(); weekend.performClick(); assertEquals(2, tapped)
                ShopCardKit.install()
                val (page, stage) = ShopCardKit.page(ctx, null, day = "2026-10-31")
                // Keep queued live-clock renders from replacing the deterministic offer fixtures.
                page.onGone()
                fun find(v: View): TextView? {
                    if (v is TextView && v.text.toString().startsWith("DEAL OF THE")) return v
                    if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
                    return null
                }
                val oldOffers = find(stage)!!.parent.parent.parent as ViewGroup
                val parent = oldOffers.parent as ViewGroup
                val index = parent.indexOfChild(oldOffers)
                val lp = oldOffers.layoutParams
                parent.removeView(oldOffers)
                parent.addView(ShopViews.offers(ctx, daily, weekend), index, lp)
                stage
            }
        }
    }

    @Test fun weekendTimerUsesEndOfSundayGameDay() {
        fun at(s: String) = LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            assertEquals("Ends in 1 d 5 h", ShopPlan.weekendEndsText("2026-11-01", 4, at("2026-10-31T23:00:00")))
            assertEquals("Ends in 1 d 0 h", ShopPlan.weekendEndsText("2026-11-01", 4, at("2026-11-01T04:00:00")))
            assertEquals("Ends in 23 h 59 min", ShopPlan.weekendEndsText("2026-11-01", 4, at("2026-11-01T04:01:00")))
            assertEquals("Ends in 5 h 12 min", ShopPlan.weekendEndsText("2026-11-01", 4, at("2026-11-01T22:48:00")))
            assertEquals("Ends in 0 h 1 min", ShopPlan.weekendEndsText("2026-11-01", 4, at("2026-11-02T03:59:30")))
            assertEquals("Ends in 4 h 0 min", ShopPlan.weekendEndsText("2026-11-01", 4, at("2026-11-02T00:00:00")))
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Warsaw"))
            assertEquals("Ends in 1 d 4 h", ShopPlan.weekendEndsText("2026-03-29", 4, at("2026-03-28T23:00:00")))
        } finally { java.util.TimeZone.setDefault(original) }
    }
}
