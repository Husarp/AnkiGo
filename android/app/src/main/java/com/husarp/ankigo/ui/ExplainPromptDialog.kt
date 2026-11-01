package com.husarp.ankigo.ui

import android.content.Context
import android.text.InputFilter
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.study.CardExplain

/** Reset edits the draft; only Save persists it. */
internal object ExplainPromptDialog {
    fun create(ctx: Context): AppDialog {
        val prefs = ctx.getSharedPreferences("study", 0)
        val field = EditText(ctx).apply {
            setText(prefs.getString(CardExplain.PREF_TEMPLATE, null) ?: CardExplain.DEFAULT_TEMPLATE)
            contentDescription = "Google browser prompt"
            minLines = 8; maxLines = 10
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            typeface = Ui.font(ctx, 700)
            textSize = 14f
            setTextColor(Ui.c(ctx, R.color.ag_text))
            filters = arrayOf(InputFilter.LengthFilter(1000))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_mantle))
                cornerRadius = Ui.dp(ctx, 10f)
                setStroke(Ui.dp(ctx, 1), Ui.c(ctx, R.color.ag_s1))
            }
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 24), 0, Ui.dp(ctx, 24), Ui.dp(ctx, 12))
            addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply {
                text = "Used by “Explain this card” in your browser."
            })
            addView(field, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(ctx, 12) })
        }
        return AppDialog.Builder(ctx).setTitle("Google browser prompt").setView(body)
            .setSideMargin(if (ctx.resources.configuration.screenWidthDp >= 360) 24 else 16)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString(CardExplain.PREF_TEMPLATE,
                    field.text.toString().trim().ifBlank { CardExplain.DEFAULT_TEMPLATE }).apply()
            }.setNeutralButton("Reset to default", null)
            .setNegativeButton("Cancel", null).create().also { dialog ->
                dialog.button(AppDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    field.setText(CardExplain.DEFAULT_TEMPLATE)
                    field.setSelection(field.length())
                    field.requestFocus()
                }
            }
    }
}
