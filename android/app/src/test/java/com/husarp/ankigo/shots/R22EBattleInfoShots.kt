package com.husarp.ankigo.shots

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.game.Type
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22EBattleInfoShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun cleanUp() = BattleShotKit.reset()

    @Test fun pickedIconsAndLiveExplanationsInBothThemes() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val st = GameRepo.state!!
            st.battle!!.field.weather = "rain"
            st.main!!.ability = "Swift Swim"
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            for (key in listOf("rain", "sun", "sand", "hail", "snow", "electric", "grassy", "misty", "psychic", "trickroom", "gravity", "wonderroom", "mudsport", "watersport", "iondeluge", "reflect", "lightscreen", "auroraveil", "safeguard", "mist", "tailwind", "wish", "mp.fs")) {
                val row = LinearLayout(ctx)
                val id = FieldIcons.of(key)
                assertNotNull(key, id)
                row.addView(ImageView(ctx).apply { setImageResource(id!!) }, LinearLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)))
                row.addView(Ui.text(ctx, 12f, 700, com.husarp.ankigo.R.color.ag_text).apply { text = ConditionMarks.def(key)!!.word })
                col.addView(row)
            }
            paparazzi.snapshot(col, "r22e-picked-icons-${theme.name.lowercase()}")
            val board = ConditionBoard().apply { syncFrom(st.battle) }
            val list = ConditionList(ctx, 8f)
            list.build(ConditionSections.of(board, ConditionMarks.Slot.FIELD, "Wild", st.main!!.name), Ui.dp(ctx, 340), Ui.dp(ctx, 240))
            assertTrue(list.spoken.contains("Swift Swim"))
            assertTrue(list.spoken.contains("Water moves ×1.5"))
            paparazzi.snapshot(list, "r22e-rain-explanation-${theme.name.lowercase()}")
        }
    }

    @Test fun playerTypesMatchWildTypesInBothLayouts() {
        for (theme in Theme.entries) for (style in InfoBoxStyle.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxStyle(ctx, style)
            val panel = BattlePanelView(ctx)
            panel.renderAll()
            assertEquals(style, panel.arena.infoBoxStyle)
            panel.arena.setPlayerTypes(listOf(Type.WATER, Type.ICE))
            paparazzi.snapshot(panel, "r22e-player-types-${style.name.lowercase()}-${theme.name.lowercase()}")
            fun texts(v: View): List<String> = when (v) {
                is TextView -> listOf(v.text.toString())
                is android.view.ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
                else -> emptyList()
            }
            assertFalse(texts(panel.arena).contains("HP"))
        }
    }
}
