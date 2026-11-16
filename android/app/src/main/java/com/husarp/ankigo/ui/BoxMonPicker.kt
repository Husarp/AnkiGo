package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import com.husarp.ankigo.game.Mon

/** The real Box in a modal, single-recipient visit, including when opened from a floating item window. */
internal object BoxMonPicker {
    internal var interceptShow: ((Dialog, TeamPage) -> Unit)? = null

    fun show(source: GamePage, title: String, eligible: (Mon) -> Boolean, picked: (Mon) -> Unit) =
        show(source, title, eligible, null, picked)

    fun show(source: GamePage, title: String, eligible: (Mon) -> Boolean, message: String?, picked: (Mon) -> Unit) {
        val dialog = Dialog(source).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val host = object : PageHost {
            override val hostActivity get() = source.host.hostActivity
            override fun closePage() = dialog.dismiss()
            override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
        }
        var chosen = false
        val page = TeamPage(source, host).apply {
            pickRecipient(TeamPage.Recipient(title, eligible) { mon ->
                if (!chosen && alive && eligible(mon)) {
                    chosen = true
                    dialog.dismiss()
                    picked(mon)
                }
            })
        }
        dialog.setContentView(if (message == null) page.root else android.widget.LinearLayout(source).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(source, com.husarp.ankigo.R.color.ag_base))
            addView(Ui.text(source, 13f, 600, com.husarp.ankigo.R.color.ag_sub0).apply {
                text = message
                setPadding(Ui.dp(source, 16), Ui.dp(source, 12), Ui.dp(source, 16), Ui.dp(source, 8))
            }, android.widget.LinearLayout.LayoutParams(-1, -2))
            addView(page.root, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        })
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
