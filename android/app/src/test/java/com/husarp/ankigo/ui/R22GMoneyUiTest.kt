package com.husarp.ankigo.ui

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.shots.Shots
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test

class R22GMoneyUiTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun resetPrefs() {
        Shots.files(paparazzi.context).getSharedPreferences("study", 0).edit().remove("reduceMotion").commit()
    }

    @Test fun initialBalanceShowsTheRealAmountAndReducedMotionUpdatesImmediately() {
        val ctx = Shots.files(paparazzi.context)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val chip = Ui.chip(ctx, R.drawable.ic_money, R.color.ag_yellow)
        chip.setText("12,345")
        assertEquals("12,345", chip.label.text.toString())
        chip.setText("10,000")
        assertEquals("10,000", chip.label.text.toString())
        chip.setText("10,075")
        assertEquals("10,075", chip.label.text.toString())
    }
    @Test fun streakChipsUpdateImmediatelyWithoutMoneyAnimation() {
        val ctx = Shots.files(paparazzi.context)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        val chip = Ui.chip(ctx, StreakFlame(ctx), R.color.ag_yellow)
        chip.setText("12")
        chip.setText("13")
        assertEquals("13", chip.label.text.toString())
        chip.setText("0")
        assertEquals("0", chip.label.text.toString())
    }

    @Test fun shopPillsHandleTheLoadingPlaceholderAndFormatTheFinalBalance() {
        val ctx = Shots.files(paparazzi.context)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val bar = AppBar(ctx, "Shop", "—", false, R.drawable.ic_money, R.color.ag_yellow)
        bar.setPill("—")
        assertEquals("—", bar.pill!!.text.toString())
        bar.setPill("1,234")
        assertEquals("1,234", bar.pill!!.text.toString())
        bar.setPill("234")
        assertEquals("234", bar.pill!!.text.toString())
        bar.setPill("")
        bar.setPill("234")
        assertEquals("234", bar.pill!!.text.toString())
    }
}
