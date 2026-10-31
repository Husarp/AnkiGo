package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** One behavior case for every formerly inactive ability, plus the three existing page entries. */
@RunWith(Parameterized::class)
class R23SAbilitiesTest(private val ab: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases(): List<Array<String>> = listOf(
            "gluttony", "pickup", "unburden", "cheekpouch", "ripen", "klutz", "pickpocket", "magician", "ballfetch", "cudchew", "honeygather", "unnerve", "stickyhold",
            "healer", "plus", "minus", "telepathy", "friendguard", "battery", "powerspot", "receiver", "commander", "dancer", "hospitality", "curiousmedicine",
            "runaway", "naturalcure", "magnetpull", "wimpout", "emergencyexit", "zerotohero", "suctioncups",
            "beastboost", "moxie", "soulheart", "chillingneigh", "grimneigh", "supremeoverlord", "pressure", "frisk", "anticipation", "forewarn", "illusion",
            "forecast", "flowergift", "stancechange", "schooling", "shieldsdown", "iceface", "hungerswitch", "gulpmissile", "multitype", "rkssystem", "terashift",
            "sniper", "superluck", "battlearmor", "shellarmor", "angerpoint", "merciless", "toxicdebris"
        ).map { arrayOf(it) }
    }
    private val data = R17C9b.data
    private val m = R17C9b.main(ab, "tackle", "watergun")
    private val w = R17C9b.wild("", 801, "tackle", "watergun")
    private val b = R17C9b.battle(w)
    private val r = BattleEngine.Round(data, b, m, 1.0, 1.0, R17C9b.low)
    private val rules = AbilityAdaptations
    private fun hit(p: Boolean = true, move: String = "tackle") = BattleEngine.newHit(r, p, r.mon(p), r.side(p), r.mon(!p), r.side(!p), data.move(move)!!)
    private fun enter() { Abilities.enter(r, true, ab); rules.onEnter(r, true) }
    private fun hasLine(word: String) = assertTrue(r.events.filterIsInstance<GameEvent.Effect>().any { word in it.text })
    private fun half() { w.hp = r.maxHp(false) / 2; rules.afterDamage(r, false, hit(), 1) }

    @Test fun abilityChangesTheSupportedScenario() {
        assertNotNull(AbilityText.of(ab))
        assertFalse(AbilityText.of(ab)!!.startsWith("No effect"))
        when (ab) {
            "gluttony", "cheekpouch", "ripen", "cudchew" -> {
                m.hp = r.maxHp(true) / 2
                m.heldItem = if (ab == "gluttony") "figy-berry" else "sitrus-berry"
                val before = m.hp
                enter()
                assertNull(m.heldItem)
                val heal = if (ab == "gluttony") r.maxHp(true) / 3 else r.maxHp(true) / 4
                val extra = if (ab == "cheekpouch") r.maxHp(true) / 3 else if (ab == "ripen") heal else 0
                assertEquals(minOf(r.maxHp(true), before + heal + extra), m.hp)
                if (ab == "cudchew") {
                    m.hp = 1; rules.endOfRound(r); assertEquals(1, m.hp)
                    b.rounds++; rules.endOfRound(r); assertEquals(1 + heal, m.hp)
                    rules.endOfRound(r); assertEquals(1 + heal, m.hp)
                }
            }
            "klutz", "unnerve" -> {
                val target = if (ab == "unnerve") w else m
                target.hp = 1; target.heldItem = "sitrus-berry"
                rules.eat(r, ab != "unnerve")
                assertEquals(1, target.hp); assertNotNull(target.heldItem)
            }
            "pickup", "honeygather" -> {
                val st = GameState(); st.mons += m; st.team += m.uid
                val events = mutableListOf<GameEvent>()
                rules.onWin(st, R17C9b.low, events)
                if (ab == "pickup") assertEquals(1, st.count("potion")) else assertEquals(5, st.cash)
                val count = events.size; rules.onWin(st, R17C9b.fixed(99), events); assertEquals(count, events.size)
            }
            "ballfetch", "runaway" -> {
                val st = GameState(); st.mons += m; st.team += m.uid; st.battle = b
                val g = Game(data, st, R17C9b.fixed(99))
                if (ab == "ballfetch") {
                    Economy.give(st, "poke-ball"); g.throwBall("poke-ball"); assertEquals(1, st.count("poke-ball"))
                } else {
                    Economy.give(st, "poke-doll"); g.escape("poke-doll"); assertEquals(1, st.count("poke-doll")); assertTrue(st.battle !== b)
                }
            }
            "unburden" -> { m.heldItem = "everstone"; enter(); m.heldItem = null; rules.afterMove(r, true, data.move("tackle")!!, false); assertEquals(2.0, rules.speedMod(r, true), 0.0); b.switchOutMain(data, m); assertEquals(2.0, rules.speedMod(r, true), 0.0) }
            "pickpocket", "magician" -> {
                w.heldItem = "everstone"
                if (ab == "magician") rules.afterHit(r, true, data.move("tackle")!!, 1) else rules.afterDamage(r, true, hit(false), 1)
                assertEquals("everstone", m.heldItem); assertNull(w.heldItem)
            }
            "stickyhold" -> {
                m.heldItem = "everstone"; w.ability = "Magician"
                rules.afterHit(r, false, data.move("tackle")!!, 1)
                assertEquals("everstone", m.heldItem); assertNull(w.heldItem)
            }
            "healer" -> { b.player.status = "par"; rules.endOfRound(r); assertNull(b.player.status) }
            "plus", "minus" -> { b.team = listOf(m, R17C9b.main("Plus", "tackle").also { it.uid = "partner" }); assertEquals(1.5, rules.attackMod(hit(move = "watergun")), 0.0) }
            "telepathy", "friendguard", "pressure", "battlearmor", "shellarmor", "illusion" -> {
                assertEquals(if (ab == "friendguard") 0.75 else 0.9, rules.finalMod(hit(false)), 0.0)
                if (ab == "illusion") { rules.afterDamage(r, true, hit(false), 1); assertEquals(1.0, rules.finalMod(hit(false)), 0.0) }
            }
            "battery", "powerspot" -> assertEquals(1.3, rules.finalMod(hit(move = "watergun")), 0.0)
            "receiver" -> { b.team = listOf(m, R17C9b.main("Huge Power", "tackle").also { it.uid = "fallen"; it.hp = 0 }, R17C9b.main("Imposter", "tackle").also { it.uid = "copying"; it.hp = 0 }); enter(); assertEquals("hugepower", r.ability(true)) }
            "commander", "terashift" -> { enter(); assertEquals(if (ab == "commander") 2 else 1, b.player.boosts[1]); enter(); assertEquals(if (ab == "commander") 2 else 1, b.player.boosts[1]) }
            "dancer" -> {
                val dance = Move("testdance", "Test Dance", Type.NORMAL, Category.STATUS, 0, null, target = "self", boosts = mapOf("atk" to 1), flags = setOf("dance"))
                rules.afterMove(r, false, dance, true); assertEquals(1, b.player.boosts[0])
                w.ability = "Dancer"; rules.afterMove(r, false, dance, false); assertEquals(2, b.player.boosts[0]); assertEquals(0, b.enemy.boosts[0])
            }
            "hospitality" -> { m.hp = 1; enter(); assertEquals(1 + r.maxHp(true) / 4, m.hp); enter(); assertEquals(1 + r.maxHp(true) / 4, m.hp) }
            "curiousmedicine" -> { b.player.boosts[0] = -2; b.player.boosts[1] = 2; enter(); assertEquals(0, b.player.boosts[0]); assertEquals(2, b.player.boosts[1]) }
            "naturalcure" -> { b.player.status = "par"; rules.onLeave(b, data, true, m); assertNull(b.player.status) }
            "magnetpull" -> { w.ability = ab; val view = object : BattleView by r { override fun types(p: Boolean) = if (p) listOf(Type.STEEL) else listOf(Type.NORMAL) }; assertNotNull(Hazards.blocker(view)); val ghost = object : BattleView by r { override fun types(p: Boolean) = listOf(Type.STEEL, Type.GHOST) }; assertNull(Hazards.blocker(ghost)) }
            "wimpout", "emergencyexit" -> { b.team = listOf(m, R17C9b.main("", "tackle").also { it.uid = "partner" }); m.hp = r.maxHp(true) / 2; if (ab == "wimpout") rules.afterDamage(r, true, hit(false), 10) else rules.afterIndirectDamage(r, true, 10); assertEquals(1, r.conds(true)[AbilityAdaptations.SWITCH_OFFER]); b.switchOutMain(data, m); assertNull(r.conds(true)[AbilityAdaptations.SWITCH_OFFER]); rules.afterDamage(r, true, hit(false), 10); assertNull(r.conds(true)[AbilityAdaptations.SWITCH_OFFER]) }
            "zerotohero" -> { rules.onLeave(b, data, true, m); assertEquals(1.5, rules.attackMod(hit()), 0.0) }
            "suctioncups" -> assertNotNull(RemainingMoves.failReason(r, false, Move("roar", "Roar", Type.NORMAL, Category.STATUS, 0, null)))
            "beastboost", "moxie", "soulheart", "chillingneigh", "grimneigh" -> {
                half(); val index = if (ab in setOf("soulheart", "grimneigh")) 2 else if (ab == "beastboost") r.stats(true).toList().drop(1).let { it.indexOf(it.max()) } else 0
                assertEquals(1, b.player.boosts[index]); half(); assertEquals(1, b.player.boosts[index])
            }
            "supremeoverlord" -> { b.team = listOf(m, R17C9b.main("", "tackle").also { it.uid = "fallen"; it.hp = 0 }); enter(); assertEquals(1.1, rules.finalMod(hit()), 0.0) }
            "frisk" -> { w.heldItem = "everstone"; enter(); hasLine("Everstone") }
            "forewarn" -> { enter(); hasLine("Tackle"); hasLine("Water Gun") }
            "anticipation" -> { w.moves.clear(); w.moves += "fissure"; enter(); hasLine("Fissure") }
            "forecast" -> { b.field.weather = "rain"; assertEquals(listOf(Type.WATER), r.types(true)); b.field.weather = "sun"; assertEquals(listOf(Type.FIRE), r.types(true)) }
            "flowergift" -> { b.field.weather = "sun"; assertEquals(1.5, rules.attackMod(hit()), 0.0); assertEquals(1.5, rules.defenseMod(hit(false, "watergun")), 0.0) }
            "stancechange" -> { m.species = 809; val base = r.baseStats(true); rules.beforeMove(r, true, data.move("tackle")!!); assertEquals(base.def, r.stats(true).atk); rules.beforeMove(r, true, Move("kingsshield", "King's Shield", Type.STEEL, Category.STATUS, 0, null)); assertEquals(base.atk, r.stats(true).atk) }
            "schooling" -> { assertEquals(1.5, rules.attackMod(hit()), 0.0); m.hp = 1; assertEquals(1.0, rules.attackMod(hit()), 0.0) }
            "shieldsdown" -> { assertNotNull(rules.blockStatus(r, true, "par", true)); m.hp = 1; assertNull(rules.blockStatus(r, true, "par", true)); assertEquals(1.5, rules.speedMod(r, true), 0.0) }
            "iceface" -> { assertEquals(0, rules.takeDamage(r, true, hit(false), 10)); assertEquals(10, rules.takeDamage(r, true, hit(false), 10)); b.field.weather = "snow"; rules.endOfRound(r); assertEquals(0, rules.takeDamage(r, true, hit(false), 10)) }
            "hungerswitch" -> { val h = BattleEngine.newHit(r, true, m, b.player, w, b.enemy, Move("aurawheel", "Aura Wheel", Type.ELECTRIC, Category.PHYSICAL, 110, 100)); assertEquals(Type.ELECTRIC, rules.moveType(h, h.type)); rules.endOfRound(r); assertEquals(Type.DARK, rules.moveType(h, h.type)) }
            "gulpmissile" -> { rules.afterMove(r, true, Move("surf", "Surf", Type.WATER, Category.SPECIAL, 90, 100), true); val before = w.hp; m.hp = 0; rules.afterDamage(r, true, hit(false), 1); assertEquals(before - r.maxHp(false) / 4, w.hp); assertEquals(-1, b.enemy.boosts[1]); assertTrue(r.events.none { it is GameEvent.Attack }) }
            "multitype", "rkssystem" -> { m.moves.clear(); m.moves += "watergun"; assertEquals(listOf(Type.WATER), r.types(true)) }
            "sniper", "superluck" -> { repeat(3) { assertEquals(1.0, rules.finalMod(hit()), 0.0); rules.afterDamage(r, false, hit(), 1) }; assertEquals(if (ab == "sniper") 1.5 else 1.25, rules.finalMod(hit()), 0.0) }
            "angerpoint" -> { rules.afterHit(r, false, data.move("tackle")!!, r.maxHp(true) / 4); assertEquals(2, b.player.boosts[0]); rules.afterHit(r, false, data.move("tackle")!!, r.maxHp(true)); assertEquals(2, b.player.boosts[0]) }
            "merciless" -> { b.enemy.status = "tox"; assertEquals(1.5, rules.finalMod(hit()), 0.0) }
            "toxicdebris" -> { rules.afterDamage(r, true, hit(false), 1); assertEquals("psn", b.enemy.status) }
            else -> error("Missing behavior case: $ab")
        }
    }
}
