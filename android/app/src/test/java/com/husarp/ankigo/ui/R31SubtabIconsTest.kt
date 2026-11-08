package com.husarp.ankigo.ui

import com.husarp.ankigo.R
import com.husarp.ankigo.game.AchievementTable
import org.junit.Assert.*
import org.junit.Test

class R31SubtabIconsTest {
    @Test fun everyAchievementGroupHasAnIconAndEconomySharesTheMoneyIcon() {
        val icons = (listOf("All") + AchievementTable.GROUPS).map(SubtabIcons::achievement)
        assertEquals(9, icons.toSet().size)
        assertEquals(R.drawable.ic_ach_money, SubtabIcons.achievement("Economy"))
    }

    @Test fun sharedNavigationHasOneIconPerTabButValueChoicesStayPlain() {
        for (labels in listOf(listOf("All", "Team", "Box"), listOf("Look", "Content"), listOf("Attack", "Defend")))
            assertEquals(labels.size, SubtabIcons.navigation(labels)!!.size)
        assertNull(SubtabIcons.navigation(listOf("Phone", "Light", "Dark")))
        assertNull(SubtabIcons.navigation(listOf("Same every day", "Per weekday")))
    }
}
