package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.GameActivity
import com.husarp.ankigo.update.Updates

/** Helpers for the main game screen (GameActivity) screenshots. */
object BattleShotKit {
    class Built(val activity: GameActivity, val root: View) {
        val panel: BattlePanelView? get() = field("panel") as? BattlePanelView
        val player: EventPlayer? get() = field("player") as? EventPlayer
        val arena: ArenaView? get() = panel?.arena

        fun field(name: String): Any? = GameActivity::class.java.getDeclaredField(name).let { it.isAccessible = true; it.get(activity) }
        fun setField(name: String, v: Any?) = GameActivity::class.java.getDeclaredField(name).let { it.isAccessible = true; it.set(activity, v) }
        fun call(name: String, vararg args: Any?): Any? {
            val m = GameActivity::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }
            m.isAccessible = true
            return m.invoke(activity, *args)
        }
    }

    /** The screen's views as onCreate builds them (activity attached to the render context, no onCreate). */
    fun build(ctx: Context): Built {
        val a = attach(GameActivity(), ctx)
        return Built(a, a.buildContent())
    }

    /**
     * The screen once the game is loaded (what bind() shows after onStatus(READY)). The bottom bar is shown when the
     * window is tall (the activity's layout listener does that after the first layout, which a single render pass skips).
     */
    fun bound(ctx: Context): Built = build(ctx).also {
        it.call("bind")
        if (ctx.resources.configuration.screenHeightDp >= 560) (it.field("nav") as View).visibility = View.VISIBLE
    }

    /** Picture-in-picture, as onPictureInPictureModeChanged(true) sets it up. */
    fun pip(b: Built, paused: Boolean = false) {
        b.setField("inPip", true)
        b.panel?.setCompact(true)
        if (paused) b.panel?.setPaused(true)
        (b.field("nav") as View).visibility = View.GONE
    }

    /** The screen's view under the floating update banner, at its final (slid-in) position, as AgActivity.setContentView does it. */
    fun withBanner(ctx: Context, content: View): View =
        com.husarp.ankigo.update.UpdateOverlay(ctx, content).also { it.banner.animated = false }

    /** Replaces the Updates state (the updater's private field); [reset] puts the default back. */
    fun setUpdates(state: Updates.State) {
        val f = Updates::class.java.getDeclaredField("state")
        f.isAccessible = true
        f.set(Updates, state)
    }

    fun reset() = setUpdates(Updates.State())

    fun newer(phase: Updates.Phase = Updates.Phase.Idle) = Updates.State(
        latest = Updates.Latest("0.4.3", "AnkiGo-0.4.3.apk", "https://example.invalid/AnkiGo-0.4.3.apk", 2_200_000L, "https://example.invalid/r"),
        phase = phase,
    )

    fun gameRepoSet(field: String, v: Any?) {
        val f = GameRepo::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(GameRepo, v)
    }
}

/**
 * Like [shoot], but sets the FakeGame scenario / sprites before every install (they reset after each one).
 * [offsetMillis] is the virtual time that passes before the picture is taken.
 */
fun app.cash.paparazzi.Paparazzi.shootWith(
    name: String,
    sizes: List<Size> = listOf(Size.PHONE, Size.SPLIT),
    scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME,
    sprites: Boolean = true,
    offsetMillis: Long = 1000,
    build: (Context) -> View,
) {
    for (size in sizes) for (theme in Theme.entries) {
        unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        FakeGame.scenario = scenario
        FakeGame.withSprites = sprites
        val ctx = Shots.files(context)
        FakeGame.install(ctx)
        if (!sprites) noDownloads(ctx)
        val v = build(ctx)
        val frame = android.widget.FrameLayout(ctx).apply {
            addView(v, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", offsetMillis)
    }
}

/**
 * Without sprite files the panel starts a background download (the machine may be online, which would fill the
 * placeholders with real pictures). "Not found" markers make Sprites.download give up at once, as for a species PokeAPI lacks.
 */
private fun noDownloads(ctx: Context) {
    val dir = com.husarp.ankigo.data.Sprites.dir(ctx)
    val now = System.currentTimeMillis().toString()
    for (sp in FakeGame.data.species.values) for (sub in listOf("front", "back", "static")) {
        for (suffix in listOf("", "s")) java.io.File(dir, "$sub/${sp.num}$suffix.missing").also { it.parentFile.mkdirs() }.writeText(now)
    }
    for (id in listOf("poke-ball", "great-ball", "ultra-ball")) java.io.File(dir, "item/$id.missing").also { it.parentFile.mkdirs() }.writeText(now)
}
