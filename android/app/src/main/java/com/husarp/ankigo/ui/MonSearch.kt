package com.husarp.ankigo.ui

import android.content.Context
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import com.husarp.ankigo.R

/** Shared name/number search styling; the owner updates its grid without replacing the field. */
internal object MonSearch {
    fun field(ctx: Context, query: String, changed: (String) -> Unit): EditText {
        return EditText(ctx).apply {
            hint = "Search name or number"
            typeface = Ui.font(ctx, 700)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Ui.c(ctx, R.color.ag_text))
            setHintTextColor(Ui.c(ctx, R.color.ag_ov))
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
            setText(query)
            Ui.tintEdit(this)
            backgroundTintList = null // tintEdit tints the underline of a plain field; this one has its own rounded fill
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { changed(s?.toString().orEmpty()) }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            })
            setOnEditorActionListener { v, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { v.clearFocus(); true } else false
            }
        }
    }
}
