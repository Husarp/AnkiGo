package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.VectorDrawable
import android.widget.ImageView
import com.husarp.ankigo.R
import com.husarp.ankigo.help.*
import com.husarp.ankigo.ui.*
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32FShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun guideMoneyIsAnOutlineInBothThemes() {
        paparazzi.shootWith("f-guide-money", listOf(Size.PHONE)) { ctx ->
            assertEquals(R.drawable.ic_ach_money, HelpViews.chapterIcon(GameGuide.MONEY))
            val drawable = ctx.getDrawable(HelpViews.chapterIcon(GameGuide.MONEY))!!
            assertTrue(drawable is VectorDrawable)
            val bitmap = android.graphics.Bitmap.createBitmap(240, 240, android.graphics.Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 240, 240)
            drawable.draw(android.graphics.Canvas(bitmap))
            assertEquals("Coin interior has no fill", 0, Color.alpha(bitmap.getPixel(80, 90)))
            assertTrue("Outline remains visible", Color.alpha(bitmap.getPixel(35, 120)) > 0)
            val activity = attach(Activity(), ctx)
            val host = object : PageHost {
                override val hostActivity: Activity get() = activity
                override fun closePage() = Unit
                override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
            }
            pageScreen(ctx, HelpScreen(ctx, host, HelpRoute.Guide(GameGuide.MONEY), false))
        }
    }

    @Test fun fallbackMatchesFullResolutionFirstFrame() {
        paparazzi.shootWith("f-full-resolution-flame", listOf(Size.PHONE)) { ctx ->
            val animated = javaClass.getResourceAsStream("/streak-flame-r33/frame-0.png")!!.use { BitmapFactory.decodeStream(it) }!!
            val still = BitmapFactory.decodeResource(ctx.resources, R.drawable.streak_flame_still)!!
            assertEquals(512, still.width)
            assertEquals(512, still.height)
            assertTrue("Fallback is the animation's first frame", animated.sameAs(still))
            ImageView(ctx).apply { setImageBitmap(still); setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
        }
    }
}
