package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R

/** The search box on the Settings home and its result list (pick 2A). Pure matching lives in [SettingsSearch]. */
internal object SettingsHomeSearch {
    /** The rounded search field: magnifier, "Search settings", a clear button once there are words. [onChange] gets every new text. */
    fun box(ctx: Context, initial: String, onChange: (String) -> Unit): View {
        val clear = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            setPadding(Ui.dp(ctx, 13), Ui.dp(ctx, 13), Ui.dp(ctx, 13), Ui.dp(ctx, 13))
            contentDescription = "Clear search"
            visibility = if (initial.isEmpty()) View.GONE else View.VISIBLE
        }
        val field = EditText(ctx).apply {
            hint = "Search settings"
            typeface = Ui.font(ctx, 700)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Ui.c(ctx, R.color.ag_text))
            setHintTextColor(Ui.c(ctx, R.color.ag_sub0))
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = null
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12))
            setText(initial)
            Ui.tintEdit(this)
            backgroundTintList = null
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val t = s?.toString().orEmpty()
                    clear.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
                    onChange(t)
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            })
            setOnEditorActionListener { v, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(v); v.clearFocus(); true } else false
            }
        }
        clear.setOnClickListener { field.setText("") }
        val icon = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx)
            setPadding(Ui.dp(ctx, 14), 0, 0, 0)
            addView(icon, Form.lp(Ui.dp(ctx, 20), Ui.dp(ctx, 20)))
            addView(field, Form.lp(0, -2, 1f))
            addView(clear, Form.lp(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
            minimumHeight = Ui.dp(ctx, 48)
        }
    }

    private fun hideKeyboard(v: View) {
        (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    /** The matches for [query]: title, one explanation line and where the setting lives (in its section's colour). */
    fun results(ctx: Context, hits: List<SettingHit>, query: String, onTap: (SettingInfo) -> Unit): View {
        if (hits.isEmpty()) return Ui.card(ctx).apply {
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 16))
            addView(Ui.text(ctx, 14f, 700, R.color.ag_text).apply { text = "Nothing matches “$query”" })
            addView(Form.body(ctx, "Try one word, like sound, timer or catch.", 12f, R.color.ag_sub0), Form.lp(ctx, 2))
        }
        return Form.cardOf(ctx, hits.map { hit -> resultRow(ctx, hit, onTap) })
    }

    private fun resultRow(ctx: Context, hit: SettingHit, onTap: (SettingInfo) -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 11), Ui.dp(ctx, 14), Ui.dp(ctx, 11))
        minimumHeight = Ui.dp(ctx, 52)
        addView(Ui.text(ctx, 14f, 700, R.color.ag_text).apply { text = hit.info.title })
        addView(Form.body(ctx, hit.info.explain, 12f, R.color.ag_sub0), Form.lp(ctx, 2))
        addView(Ui.text(ctx, 11f, 800, SettingsSearch.sectionOf(hit.info).tone).apply { text = hit.where }, Form.lp(ctx, 3))
        contentDescription = "${hit.info.title}. ${hit.info.explain}. ${hit.where}"
        isClickable = true
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), null, ColorDrawable(0xFFFFFFFF.toInt()))
        setOnClickListener { onTap(hit.info) }
    }

    /** Tints [row] with the accent for a moment, so the player sees which setting the search led to. */
    fun flash(row: View) {
        val old = row.background
        row.background = ColorDrawable((Ui.c(row.context, R.color.ag_mauve) and 0x00FFFFFF) or 0x33000000)
        row.postDelayed({ row.background = old }, 1800)
    }
}
