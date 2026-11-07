package com.husarp.ankigo.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import com.husarp.ankigo.game.*

/** Item recipients use the Box's sort sheet, type filters and independent Show filters. */
internal object OwnedMonPicker {
    fun show(page: GamePage, title: String, eligible: (Mon) -> Boolean, picked: (Mon) -> Unit) {
        lateinit var dialog: AppDialog
        val content = content(page, eligible) { dialog.dismiss(); picked(it) }
        dialog = AppDialog.Builder(page).setTitle(title).setView(content, scrollsItself = true)
            .setNegativeButton("Cancel", null).create()
        Pickers.show(dialog)
    }

    internal fun content(page: GamePage, eligible: (Mon) -> Boolean, picked: (Mon) -> Unit): View {
        var spec = SortPrefs.load(page, SortPrefs.Where.BOX)
        var types = TypeFilter()
        var rarity = RarityFilter()
        var filters = emptySet<BoxFilter>()
        var query = ""
        var shown = emptyList<Mon>()
        val controls = LinearLayout(page).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(page, 16), 0, Ui.dp(page, 16), 0)
        }
        val list = MotionListView(page).apply { divider = null }
        fun matches(): List<Mon> = BoxTiles.filter(page.state.mons.filter(eligible), filters, page.state.team,
            page.state.settings.xpShareUid, { MonTags.canEvolve(page.data, page.state, it) }, types,
            { page.data.sp(it.species).types }).filter {
            rarity.matches(it.tier, page.data.sp(it.species).base.total) &&
                (it.shownName.contains(query.trim(), true) || it.name.contains(query.trim(), true))
        }
        fun render() {
            shown = MonSort.sort(page.data, page.state, matches(), spec)
            (list.adapter as? BaseAdapter)?.notifyDataSetChanged()
        }
        fun sheet() = SortSheet(page, SortText.boxKeys, spec, MonSort.vsWildAvailable(page.state), null, 0, null,
            count = { matches().size }, onChange = { next, _ -> spec = next; render() },
            type = SortSheet.TypePart(types) { types = it; render() },
            shows = SortSheet.ShowsPart(filters) { filters = it; render() },
            rarity = SortSheet.RarityPart(rarity) { rarity = it; render() })
        controls.addView(Ui.text(page, 14f, 900, com.husarp.ankigo.R.color.ag_mauve).apply {
            text = "Sort & filter"; minHeight = Ui.dp(page, 48)
            setOnClickListener { sheet().show() }
        })
        controls.addView(EditText(page).apply {
            hint = "Search by name"; setSingleLine(); Ui.tintEdit(this)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun afterTextChanged(s: Editable?) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); render() }
            })
        })
        list.adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val mon = shown[position]
                return Bits.monRow(page, page.data, mon, mon.heldItem?.let { "Holds ${Items.name(it)} · returns to Bag" } ?: "Holds nothing")
            }
        }
        list.setOnItemClickListener { _, _, position, _ -> shown.getOrNull(position)?.let(picked) }
        controls.addView(list, LinearLayout.LayoutParams(-1, Ui.dp(page, 320), 1f))
        val empty = Ui.text(page, 13f, 700, com.husarp.ankigo.R.color.ag_sub0).apply {
            text = "No Pokémon match these filters"; gravity = android.view.Gravity.CENTER
            minHeight = Ui.dp(page, 80)
        }
        controls.addView(empty)
        list.emptyView = empty
        render()
        return controls
    }
}
