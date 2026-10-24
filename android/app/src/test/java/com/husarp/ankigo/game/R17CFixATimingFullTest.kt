package com.husarp.ankigo.game

/**
 * R17C FIX-A (review item 18): every test of [R17C4TimingTest] once more, now under the FULL [BattleRules.ALL] with the script first (the original
 * runs with the script and [MovePower] only). Fake Out's flinch, Sucker Punch against Protect, Counter against a Substitute, the charge and lock
 * rules ... meet the other rule sets; the script yields to the rules' own forced move (a charge hit, a lock) instead of overriding it.
 */
class R17CFixATimingFullTest : R17C4TimingTest() {
    override fun rulesFor(script: R17C4Fixture.Script): List<BattleRules> = listOf<BattleRules>(script) + BattleRules.ALL
}
