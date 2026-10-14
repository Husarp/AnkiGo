package com.husarp.ankigo.overlay

import com.husarp.ankigo.ui.BattleScreen
import org.junit.Assert.*
import org.junit.Test

class FloatBattlePolicyTest {
    @Test fun leavingTheAppShowsBattleButReturningToAnyScreenRemovesIt() {
        assertTrue(FloatBattlePolicy.shouldRun(true, true, false, true))
        assertFalse(FloatBattlePolicy.shouldRun(true, true, true, true))
    }
    @Test fun setupPermissionRevocationAndOtherLeaveModesDoNotRunService() {
        assertFalse(FloatBattlePolicy.shouldRun(true, true, false, false))
        assertFalse(FloatBattlePolicy.shouldRun(true, false, false, true))
        assertFalse(FloatBattlePolicy.shouldRun(false, true, false, true))
    }
    @Test fun overlappingActivitiesAndSetupScreensStayVisibleUntilLastOneStops() {
        val game = Any(); val setup = Any(); val diagnostic = Any()
        try {
            BattleScreen.setVisible(game, true); BattleScreen.setVisible(setup, true)
            BattleScreen.setVisible(game, false)
            assertTrue(BattleScreen.visible)
            BattleScreen.setVisible(diagnostic, true); BattleScreen.setVisible(setup, false)
            assertTrue(BattleScreen.visible)
            BattleScreen.setVisible(diagnostic, false)
            assertFalse(BattleScreen.visible)
        } finally {
            listOf(game, setup, diagnostic).forEach { BattleScreen.setVisible(it, false) }
        }
    }
}
