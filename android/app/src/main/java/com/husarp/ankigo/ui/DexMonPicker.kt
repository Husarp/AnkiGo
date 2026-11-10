package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import com.husarp.ankigo.game.Mon

/** The real Pokédex in a modal, single-recipient visit, including when opened from a floating item window. */
internal object DexMonPicker {
    internal var interceptShow: ((Dialog, DexPage) -> Unit)? = null

    fun show(source: GamePage, title: String, eligible: (Mon) -> Boolean, picked: (Mon) -> Unit) {
        val dialog = Dialog(source).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val host = object : PageHost {
            override val hostActivity get() = source.host.hostActivity
            override fun closePage() = dialog.dismiss()
            override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
        }
        var chosen = false
        val page = DexPage(source, host).apply {
            pickRecipient(DexPage.Recipient(title, { sp -> source.state.mons.any { it.species == sp.num && eligible(it) } }) { sp ->
                val copies = source.state.mons.filter { it.species == sp.num && eligible(it) }
                fun choose(mon: Mon) {
                    if (!chosen && alive && eligible(mon) && source.state.mons.any { it.uid == mon.uid }) {
                        chosen = true
                        dialog.dismiss()
                        picked(mon)
                    }
                }
                if (copies.size == 1) choose(copies.single())
                else if (copies.isNotEmpty()) Pickers.pickMon(source, "Which ${sp.name}?", source.data, copies, { "${it.moves.size}/4 moves" }) { choose(it) }
            })
        }
        dialog.setContentView(page.root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { page.onHide(); page.onGone() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (Ui.findActivity(source) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        page.onShow()
        interceptShow?.let { it(dialog, page); return }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        AwayWindows.track(dialog) { it.dismiss() }
    }
}
