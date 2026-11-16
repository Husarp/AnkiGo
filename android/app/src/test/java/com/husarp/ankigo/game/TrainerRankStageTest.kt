package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainerRankStageTest {
    @Test fun allExistingRanksMapEvenlyToFiveStages() {
        val data = TestData.world()
        val ranks = listOf("Novice Trainer", "Rookie", "Skilled Trainer", "Veteran", "Advanced Trainer",
            "Elite Trainer", "Elite", "Master Trainer", "Champion", "Grand Champion", "Legendary Trainer")
        val caught = listOf(0, 150, 250, 300, 350, 400, 500, 600, 700, 800, 900)
        val levels = listOf(1, 10, 20, 30, 40, 45, 50, 60, 70, 80, 100)
        val top = listOf(5, 30, 40, 50, 60, 70, 75, 80, 90, 95, 99)
        val shiny = listOf(0, 0, 1, 1, 2, 3, 5, 10, 20, 25, 50)
        val stages = listOf(1, 1, 2, 2, 3, 3, 3, 4, 4, 5, 5)
        for (i in ranks.indices) {
            val state = GameState().apply {
                trainerLevel = levels[i]
                repeat(caught[i]) { this.caught += it + 1 }
                badges.addAll(1..8)
                repeat(shiny[i].coerceAtLeast(1)) { n ->
                    mons += TestData.mon(data, 25, top[i], uid = "rank-$n").also { it.shiny = n < shiny[i] }
                }
            }
            assertEquals(ranks[i], Trainer.rank(state))
            assertEquals(ranks[i], stages[i], Trainer.rankStage(state))
        }
    }

    @Test fun levelAloneDoesNotPromoteRankIcon() {
        assertEquals(1, Trainer.rankStage(GameState().apply { trainerLevel = 100 }))
    }
}
