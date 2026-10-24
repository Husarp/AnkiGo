package com.husarp.ankigo.study

/**
 * R18 FIX-1 (RV-9): the studied deck's learning cards are read ONCE per load. A confirmed card gets up to [NextCard.AHEAD] + 1 prepared
 * cards after it (R18-A, to answer ahead of a slow write), and each of them used to do its own AnkiDroid `searchCards` learning read
 * on the one io thread (3 reads per card, the extra ones thrown away at the next confirm). The Prepares of one load [token] now share the
 * first read that worked: [PredictRule.check] ages a read by [PredictRule.LearnRead.at] and `NextCard.unburst` covers the cards
 * answered meanwhile, so a read some milliseconds older changes nothing. A failed read ([PredictRule.LearnRead.due] null) is not kept:
 * the next Prepare asks again. Io thread only (one executor runs every provider call), so no locking.
 */
class LearnCache(private val read: () -> PredictRule.LearnRead) {
    private var token = 0
    private var last: PredictRule.LearnRead? = null

    /** The learning read for the Prepares of load [token]: the kept one, or a new read (kept when it worked). */
    fun get(token: Int): PredictRule.LearnRead {
        last?.let { if (this.token == token) return it }
        val fresh = read()
        if (fresh.due != null) { this.token = token; last = fresh } else last = null
        return fresh
    }
}
