package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*

internal object BoxSearch {
    fun matches(data: GameData, mon: Mon, query: String): Boolean {
        val q = query.trim().removePrefix("#")
        val sp = data.sp(mon.species)
        return q.isEmpty() || sp.name.contains(q, true) || mon.shownName.contains(q, true) ||
            (q.all { it.isDigit() } && (if (q.startsWith("0")) "%03d".format(sp.baseNum) else sp.baseNum.toString()).startsWith(q))
    }
}
