package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.GameState
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "always catch regional forms" setting in the save (T37): key protectRegional, a missing key means on. */
class RegionalSettingTest {
    private val mon = """"mons":[{"uid":"a","species":25,"level":5}]"""

    @Test fun anOldSaveWithoutTheKeyHasItOn() {
        assertTrue(SaveCodec.decode("""{"schema":1,$mon}""").settings.protectRegional)
        assertTrue(SaveCodec.decode("""{"schema":1,$mon,"settings":{"protectStarter":false}}""").settings.protectRegional)
    }

    @Test fun offRoundTrips() {
        val s = GameState()
        s.settings.protectRegional = false
        val json = SaveCodec.encode(s)
        assertFalse(JSONObject(json).getJSONObject("settings").getBoolean("protectRegional"))
        assertFalse(SaveCodec.decode(json).settings.protectRegional)
        s.settings.protectRegional = true
        assertTrue(SaveCodec.decode(SaveCodec.encode(s)).settings.protectRegional)
    }

    @Test fun anExplicitValueIsRead() {
        assertFalse(SaveCodec.decode("""{"schema":1,$mon,"settings":{"protectRegional":false}}""").settings.protectRegional)
        assertTrue(SaveCodec.decode("""{"schema":1,$mon,"settings":{"protectRegional":true}}""").settings.protectRegional)
    }
}
