package com.husarp.ankigo.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*

/** PLAN 279A: full-width rows, indented by stage; conditions keep their own readable space. */
internal class EvolutionRows(
    a: Context, private val data: GameData, root: EvoNode, private val current: Species,
    private val seen: Set<Int>, private val onSpecies: ((Species) -> Unit)?,
    private val state: GameState? = com.husarp.ankigo.GameRepo.state.takeIf { com.husarp.ankigo.GameRepo.data === data },
    private val mon: Mon? = null,
    private val onEvolve: ((Evolution) -> Unit)? = null,
) : LinearLayout(a) {
    private var bubble: AppDialog? = null
    private data class Branch(val row: View, val parent: View, val depth: Int, val names: View)
    private val branches = ArrayList<Branch>()

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Ui.c(context, R.color.ag_s1); strokeWidth = Ui.dp(context, 1.5f)
        }
        branches.forEach { branch ->
            val x = Ui.dp(context, 6 + branch.depth * 16 - 8).toFloat()
            val y = branch.row.top + branch.names.top + branch.names.height / 2f
            canvas.drawLine(x, branch.parent.bottom.toFloat(), x, y, paint)
            canvas.drawLine(x, y, x + Ui.dp(context, 6), y, paint)
        }
        super.dispatchDraw(canvas)
    }

    init {
        orientation = VERTICAL
        if (root.children.isEmpty() && root.via == null) {
            addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "Does not evolve" })
        } else append(root, null, 0)
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { bubble?.dismiss(); bubble = null }
        })
    }

    private fun append(node: EvoNode, parent: Species?, depth: Int, parentRow: View? = null) {
        val a = context
        val caught = node.species.num in state?.caught.orEmpty()
        val visible = caught || EvoLine.isSeen(node.species, seen)
        val here = node.species.num == current.num
        val row = object : LinearLayout(a) {
            private var stacked = false
            private val gap = Ui.dp(a, 4)
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                stacked = childCount == 3 && View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
                    available < Ui.dp(a, 250f * resources.configuration.fontScale)
                if (!stacked) { super.onMeasure(widthMeasureSpec, heightMeasureSpec); return }
                val icon = getChildAt(0)
                val names = getChildAt(1)
                val condition = getChildAt(2)
                val margins = names.layoutParams as LayoutParams
                val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                icon.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(a, 32), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(Ui.dp(a, 32), View.MeasureSpec.EXACTLY))
                names.measure(View.MeasureSpec.makeMeasureSpec(
                    (available - icon.measuredWidth - margins.marginStart - margins.marginEnd).coerceAtLeast(1),
                    View.MeasureSpec.EXACTLY), free)
                condition.measure(View.MeasureSpec.makeMeasureSpec(available.coerceAtLeast(1), View.MeasureSpec.EXACTLY), free)
                setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), resolveSize(
                    maxOf(minimumHeight, paddingTop + maxOf(icon.measuredHeight, names.measuredHeight) + gap +
                        condition.measuredHeight + paddingBottom), heightMeasureSpec))
            }
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                if (!stacked) { super.onLayout(changed, l, t, r, b); return }
                val icon = getChildAt(0)
                val names = getChildAt(1)
                val condition = getChildAt(2)
                val head = maxOf(icon.measuredHeight, names.measuredHeight)
                val iconTop = paddingTop + (head - icon.measuredHeight) / 2
                icon.layout(paddingLeft, iconTop, paddingLeft + icon.measuredWidth, iconTop + icon.measuredHeight)
                val nameLeft = paddingLeft + icon.measuredWidth + (names.layoutParams as LayoutParams).marginStart
                val nameTop = paddingTop + (head - names.measuredHeight) / 2
                names.layout(nameLeft, nameTop, nameLeft + names.measuredWidth, nameTop + names.measuredHeight)
                val below = paddingTop + head + gap
                condition.layout(paddingLeft, below, width - paddingRight, below + condition.measuredHeight)
            }
        }.apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(a, 48)
            setPadding(Ui.dp(a, 6 + depth * 16), Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 4))
            if (here) background = Ui.rounded(Bits.blend(Ui.c(a, R.color.ag_mauve), Ui.c(a, R.color.ag_mantle), .22f), 8, a)
        }
        row.addView(ImageView(a).apply {
            MonPreview.show(a, node.species.num, false, this, silhouette = !visible)
            if (!visible) {
                setColorFilter(android.graphics.Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN)
                alpha = 1f
            } else if (!caught) {
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
                alpha = 0.55f
            }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(Ui.dp(a, 32), Ui.dp(a, 32)))
        val names = LinearLayout(a).apply {
            orientation = VERTICAL
            addView(Ui.text(a, 12f, if (here) 900 else 800, if (!caught) R.color.ag_sub0 else R.color.ag_text).apply {
                text = if (visible) node.species.name.removeSuffix("-${node.species.form}") else "???"
            })
            if (visible) node.species.form?.let { form ->
                addView(Ui.text(a, 10f, 700, R.color.ag_sub0).apply { text = form })
            }
            contentDescription = EvoLine.shownName(node.species, seen) + if (here) " (this one)" else ""
            if (visible && !here && onSpecies != null) setOnClickListener { onSpecies.invoke(node.species) }
        }
        row.addView(names, LayoutParams(0, -2, 1f).apply { marginStart = Ui.dp(a, 6); marginEnd = Ui.dp(a, 6) })
        node.via?.let { e ->
            val met = isReady(e, parent)
            val condition = LinearLayout(a).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(Ui.c(a, R.color.ag_s0), 5, a)
                minimumHeight = Ui.dp(a, 40)
                setPadding(Ui.dp(a, 6), Ui.dp(a, 5), Ui.dp(a, 6), Ui.dp(a, 5))
            }
            condition.addView(ImageView(a).apply {
                setImageResource(R.drawable.ic_evolve)
                setColorFilter(Ui.c(a, R.color.ag_green))
                contentDescription = if (met) "Ready to evolve" else "Evolution requirements"
            }, LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)))
            val item = Progression.evolutionItem(e)
            if (item != null) condition.addView(ImageView(a).apply {
                Ui.itemIcon(a, item, this)
                if (!met) colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)))
            condition.addView(Ui.text(a, 11f, 800, if (met) R.color.ag_green else R.color.ag_sub0).apply {
                text = EvolutionRowText.condition(data, e, mon?.takeIf { parent?.num == it.species })
            }, LayoutParams(0, -2, 1f).apply { marginStart = Ui.dp(a, 4) })
            condition.contentDescription = "Explain ${EvolutionRowText.condition(data, e, mon?.takeIf { parent?.num == it.species })}"
            condition.setOnClickListener { showExplanation(e, parent) }
            row.addView(condition, LayoutParams(0, -2, 1.35f))
        }
        addView(row, LayoutParams(-1, -2).apply { topMargin = Ui.dp(a, 3) })
        if (parentRow != null) branches += Branch(row, parentRow, depth, names)
        node.children.forEach { append(it, node.species, depth + 1, row) }
    }

    private fun isReady(e: Evolution, parent: Species?): Boolean =
        state != null && mon != null && parent?.num == mon.species && state.mon(mon.uid) != null &&
            e in Progression.readyEvolutions(data, state, mon, System.currentTimeMillis())

    private fun showExplanation(e: Evolution, parent: Species?) {
        bubble?.dismiss()
        val a = context
        var info: AppDialog? = null
        val body = explanationContent(a, data, e, parent, seen, state, mon) { bag ->
            bubble?.dismiss()
            info?.dismiss()
            if (bag) ShopActivity.openBag(a) else Nav.open(a, Dest.SHOP)
        }
        if (onEvolve != null && mon != null && parent?.num == mon.species) {
            val dialog = AppDialog.Builder(a).setInfoHeader(EvolutionRowText.title(data, e, seen), Progression.evolutionItem(e)).setView(body)
                .setNegativeButton("Close", null)
            dialog.setPositiveButton("Hold to evolve", null)
            val window = dialog.create()
            info = window
            window.button(AppDialog.BUTTON_POSITIVE)!!.apply {
                isEnabled = isReady(e, parent)
                if (!isEnabled) {
                    background = Ui.rounded(Ui.c(a, R.color.ag_s0), 12, a)
                    setTextColor(Ui.c(a, R.color.ag_sub0))
                }
                HoldToConfirm.bind(this, "evolve", allowed = { isReady(e, parent) }) {
                    window.dismiss()
                    onEvolve.invoke(e)
                }
            }

            bubble = window
            Pickers.show(window)
            return
        }
        val dialog = AppDialog.Builder(a)
            .setInfoHeader(EvolutionRowText.title(data, e, seen), Progression.evolutionItem(e))
            .setView(body).create()
        info = dialog
        bubble = dialog
        Pickers.show(dialog)
    }

    companion object {
        internal fun explanationDialog(a: Context, data: GameData, e: Evolution, parent: Species?, seen: Set<Int>,
                                       state: GameState?, mon: Mon?, open: (Boolean) -> Unit): AppDialog =
            AppDialog.Builder(a).setInfoHeader(EvolutionRowText.title(data, e, seen), Progression.evolutionItem(e))
                .setView(explanationContent(a, data, e, parent, seen, state, mon, open)).create()

        internal fun explanationContent(a: Context, data: GameData, e: Evolution, parent: Species?, seen: Set<Int>,
                                        state: GameState?, mon: Mon?, open: (Boolean) -> Unit): View = LinearLayout(a).apply {
            orientation = VERTICAL
            setPadding(Ui.dp(a, 24), 0, Ui.dp(a, 24), Ui.dp(a, 8))
            val item = Progression.evolutionItem(e)
            val text = EvolutionRowText.explanation(data, e, parent, seen, state, mon)
            val paragraphs = text.split("\n\n").filter { it.isNotBlank() }
            addView(ItemCard.factBlock(a, "Requirements", ""), LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 12) })
            EvolutionRowText.requirements(data, e, parent, state, mon).forEach { requirement ->
                addView(Ui.text(a, 13.5f, 800, if (requirement.met) R.color.ag_green else R.color.ag_sub1).apply {
                    this.text = "${if (requirement.met) "✓" else "•"} ${requirement.label}"
                    setPadding(0, Ui.dp(a, 5), 0, Ui.dp(a, 5))
                }, LayoutParams(-1, -2))
            }
            val details = (if (item == null) paragraphs else paragraphs.drop(2)).filterNot {
                it.startsWith("Level ") && it.contains("Bond") || it.startsWith("You own ") ||
                    it.startsWith("You don't have ") || it.startsWith("Requires 1 ")
            }.joinToString("\n\n")
            val sections = if (item == null) listOf("How to evolve" to details) else
                listOf("How to use" to listOf(paragraphs.firstOrNull().orEmpty(), details).filter { it.isNotBlank() }.joinToString("\n\n"),
                    "Where to find" to paragraphs.getOrNull(1).orEmpty())
            sections.filter { it.second.isNotBlank() }.forEach { (heading, paragraph) ->
                addView(ItemCard.factBlock(a, heading, ""), LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 12) })
                addView(Ui.text(a, 13.5f, 700, R.color.ag_sub1).apply {
                    setLineSpacing(0f, 1.1f)
                    val linked = SpannableString(paragraph)
                    for ((label, bag) in listOf("open your Bag" to true, "Shop" to false)) {
                        val start = paragraph.indexOf(label)
                        if (start >= 0) linked.setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) { open(bag) }
                            override fun updateDrawState(ds: TextPaint) {
                                ds.color = Ui.c(a, R.color.ag_mauve); ds.isUnderlineText = true
                            }
                        }, start, start + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    this.text = linked
                    movementMethod = LinkMovementMethod.getInstance()
                })
            }
            if (mon != null && parent?.num == mon.species) addView(Ui.text(a, 13f, 800, R.color.ag_text).apply {
                this.text = "This can't be undone."
                setPadding(0, Ui.dp(a, 12), 0, 0)
            })
        }
    }
}

internal object EvolutionRowText {
    data class Requirement(val label: String, val met: Boolean)

    fun title(data: GameData, e: Evolution, seen: Set<Int>): String =
        "Evolution · ${data.species[e.to]?.let { EvoLine.shownName(it, seen) } ?: "???"}"

    fun requirements(data: GameData, e: Evolution, parent: Species?, state: GameState?, mon: Mon?,
                     now: Long = System.currentTimeMillis()): List<Requirement> = buildList {
        val current = mon?.takeIf { parent?.num == it.species }
        add(Requirement("Level ${Bond.requiredLevel(e)} Bond", current?.let { Bond.allows(it, e) } == true))
        e.level?.let { level -> add(Requirement("Level $level", current != null && current.level >= level)) }
        Progression.evolutionItem(e)?.let { item ->
            add(Requirement("Use ${Items.name(item)}", state != null && state.count(item) > 0))
        }
        e.move?.let { move -> add(Requirement("knows ${data.move(move)?.name ?: move}", current != null && move in current.moves)) }
        e.moveType?.let { type -> add(Requirement("knows a ${type.label} move", current?.moves?.any { data.move(it)?.type == type } == true)) }
        e.time?.let { time -> add(Requirement(if (time == "day") "in the daytime" else "at night",
            if (time == "day") Progression.isDay(now) else !Progression.isDay(now))) }
        e.gender?.let { gender -> add(Requirement(if (gender == 'M') "Male Pokémon" else "Female Pokémon", current?.gender == gender)) }
        e.condition?.replace("during the day", "")?.replace("at night", "")?.trim()?.takeIf { it.isNotEmpty() && e.kind != EvoKind.OTHER }?.let { condition ->
            val met = current != null && when (condition) {
                "with an Atk stat > its Def stat" -> StatCalc.stats(data, current).let { it.atk > it.def }
                "with an Atk stat < its Def stat" -> StatCalc.stats(data, current).let { it.atk < it.def }
                "with an Atk stat equal to its Def stat" -> StatCalc.stats(data, current).let { it.atk == it.def }
                "with a Dark-type in the party" -> state?.teamMons()?.any { it.uid != current.uid && Type.DARK in data.sp(it.species).types } == true
                else -> false
            }
            add(Requirement(condition.replaceFirstChar { it.uppercase() }, met))
        }
    }

    fun condition(data: GameData, e: Evolution, mon: Mon? = null): String {
        val method = EvoLine.method(data, e)
            .replace(Regex("^Lv "), "Level ")
            .replace(", day", "\nin the daytime").replace(", night", "\nat night")
            .replace(", Fairy-type move", "\nknows a Fairy move")
        return method + if (e.kind != EvoKind.FRIENDSHIP && mon != null && !Bond.allows(mon, e)) "\nLevel ${Bond.requiredLevel(e)} Bond" else ""
    }

    fun explanation(data: GameData, e: Evolution, parent: Species?, seen: Set<Int>, state: GameState?, mon: Mon?): String {
        val item = Progression.evolutionItem(e)
        val extra = if (item != null) {
            "Open a Pokémon’s Evolution section. Hold Evolve for one second to evolve; one ${Items.name(item)} is used up." +
                "\n\n${ItemInfo.whereToFind(data, item)}\n\n"
        } else ""
        val inventory = if (item == null) "" else (state?.let {
            if (it.count(item) == 0) "You don't have a ${Items.name(item)} in your Bag."
            else ItemInfo.plain(ItemInfo.ownLine(it, item)) + " ✓"
        } ?: "Requires 1 ${Items.name(item)}.") + "\n\n"
        val applies = mon != null && parent?.num == mon.species
        val bond = "Level ${Bond.requiredLevel(e)} Bond" +
            (if (applies && Bond.allows(mon!!, e)) " ✓" else "") + "\n\n"
        val details = EvoLine.explanation(data, e).split("\n\n").filterNot { it.startsWith("Reach Level") }.joinToString("\n\n")
        // The item paragraph already explains availability; retain all extra restrictions from the existing rules text.
        return if (item == null) bond + extra + details else extra + bond + inventory + details.split("\n\n").filterNot { it.startsWith("Have ") || it.startsWith("When ready") }.joinToString("\n\n")
    }
}
