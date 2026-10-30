package com.husarp.ankigo.sound

import com.husarp.ankigo.game.Type

/** PLAN 160: plan-moves.html set A, rendered by the same soft synth as the existing cues. */
object TypeSfx {
    fun cue(event: com.husarp.ankigo.game.GameEvent, enabled: Boolean): Cue.Effect? =
        (event as? com.husarp.ankigo.game.GameEvent.Attack)?.takeIf { enabled && !it.failed && !it.missed }
            ?.let { Cue.Effect(id(it.moveType ?: Type.NORMAL), 300L) }

    fun id(type: Type) = SoundId.valueOf("TYPE_${type.name}")
    fun type(id: SoundId): Type? = if (id.name.startsWith("TYPE_")) Type.valueOf(id.name.removePrefix("TYPE_")) else null
    fun samples(type: Type): ShortArray {
        val p = SoftSfx.Pen()
        when (type) {
            Type.NORMAL -> { p.s(0.0,200.0,90.0,.14,.45);p.x(0.0,.06,.3, 'l', 2200.0, 900.0); }
            Type.FIRE -> { p.x(0.0,.45,.45, 'b', 500.0, 2200.0, .9);p.x(0.0,.35,.2, 'l', 2600.0, 600.0);for (t in doubleArrayOf(.05,.12,.2,.28,.35))p.x(t,.025,.3, 'b', 3000.0, 3000.0, 3.0);p.s(0.0,150.0,320.0,.3,.2); }
            Type.WATER -> { p.x(0.0,.18,.4, 'l', 2400.0, 700.0);p.s(.02,900.0,320.0,.14,.35);p.s(.16,1300.0,500.0,.1,.28);p.n(.28,1760.0,.06,.18); }
            Type.ELECTRIC -> { doubleArrayOf(1500.0,900.0,1800.0,700.0,2000.0,800.0,1600.0,1000.0).forEachIndexed { i, f -> p.n(i*.03,f,.045,.3,tri = true) };p.x(0.0,.26,.2, 'b', 3500.0, 3500.0, 2.0); }
            Type.GRASS -> { p.x(0.0,.14,.4, 'b', 2600.0, 1800.0, .8);p.x(.12,.16,.35, 'b', 2200.0, 3200.0, .8);p.x(.26,.12,.25, 'b', 3000.0, 2000.0, .8); }
            Type.ICE -> { p.n(0.0,1568.0,.26,.3,bell = true);p.n(.04,2093.0,.26,.28,bell = true);p.n(.08,2637.0,.3,.22,bell = true);p.x(0.0,.12,.2, 'h', 6000.0, 6000.0);p.s(.12,300.0,120.0,.12,.3); }
            Type.FIGHTING -> { p.s(0.0,150.0,50.0,.18,.65);p.x(0.0,.05,.45, 'l', 1800.0, 600.0);p.s(.1,180.0,60.0,.14,.45); }
            Type.POISON -> { p.s(0.0,260.0,520.0,.12,.42);p.s(.13,300.0,600.0,.14,.42);p.s(.28,220.0,440.0,.17,.38);p.x(0.0,.4,.08, 'b', 800.0, 1400.0); }
            Type.GROUND -> { p.x(0.0,.5,.5, 'l', 260.0, 90.0);p.s(0.0,90.0,45.0,.35,.65);p.x(.28,.12,.3, 'l', 900.0, 300.0); }
            Type.FLYING -> { p.x(0.0,.28,.35, 'b', 600.0, 1700.0, .7);p.x(.24,.28,.3, 'b', 1700.0, 700.0, .7); }
            Type.PSYCHIC -> { p.n(0.0,880.0,.5,.26,vibHz = 6.0, vibDepth = 12.0);p.n(.02,889.0,.5,.2,vibHz = 5.0, vibDepth = 10.0);p.n(.12,1320.0,.4,.16,bell = true);p.n(.2,1760.0,.4,.12,bell = true); }
            Type.BUG -> { for (i in 0 until 6)p.n(i*.045,3000.0+(i%2)*400.0,.035,.22,tri = true);p.x(0.0,.25,.1, 'h', 5000.0, 5000.0); }
            Type.ROCK -> { p.s(0.0,120.0,60.0,.2,.55);p.x(0.0,.08,.45, 'l', 1400.0, 500.0);for (t in doubleArrayOf(.1,.17,.24))p.x(t,.04,.3, 'l', 1000.0, 400.0); }
            Type.GHOST -> { p.s(0.0,520.0,260.0,.55,.36,vibHz = 6.0, vibDepth = 14.0);p.x(0.0,.5,.1, 'b', 1200.0, 700.0, 1.5); }
            Type.DRAGON -> { p.s(0.0,110.0,60.0,.5,.5,tri = true);p.x(0.0,.4,.3, 'l', 700.0, 200.0);p.s(.05,220.0,120.0,.4,.2,tri = true); }
            Type.DARK -> { p.s(0.0,260.0,70.0,.45,.5);p.x(0.0,.3,.22, 'l', 900.0, 200.0);p.n(.1,98.0,.4,.3); }
            Type.STEEL -> { p.n(0.0,1000.0,.35,.3,bell = true);p.n(0.0,2760.0,.2,.16);p.n(0.0,5400.0,.12,.08);p.x(0.0,.03,.4, 'h', 4000.0, 4000.0); }
            Type.FAIRY -> { p.n(0.0,2093.0,.2,.3,bell = true);p.n(.06,2637.0,.2,.28,bell = true);p.n(.12,3136.0,.2,.26,bell = true);p.n(.18,4186.0,.28,.2,bell = true); }
        }
        // Below the effectiveness cue; Steel's bright partials get another small reduction.
        return p.finish(if (type == Type.STEEL) .24 else .30, false)
    }
}
