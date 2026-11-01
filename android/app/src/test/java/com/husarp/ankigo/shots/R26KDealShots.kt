package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Rule
import org.junit.Test

class R26KDealShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Test fun dayAndNightCards() {
        for (night in listOf(false, true)) paparazzi.shoot("r26k-deal-${if (night) "night" else "day"}", listOf(Size.SPLIT)) { ctx ->
            FakeGame.install(Shots.files(ctx))
            ShopViews.dealCard(ctx, "super-potion", "Super Potion", ShopPlan.Price(490,700),
                "Ends in 2 h 53 min", "Super Potion, deal, 490 instead of 700", night = night) {}
        }
    }
}
