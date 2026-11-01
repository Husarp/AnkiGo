package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch

/** Per-picture choices; stale ids never count towards the last-enabled guard. */
internal object BackdropPrefs {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE)
    fun disabled(ctx: Context): Set<String> = prefs(ctx).getStringSet("disabledBackdrops", emptySet()).orEmpty().toSet()
    fun setEnabled(ctx: Context, id: String, on: Boolean): Boolean {
        if (BackdropCatalog.byId(id) == null) return false
        val next = disabled(ctx).toMutableSet().apply { if (on) remove(id) else add(id) }
        if (BackdropCatalog.ALL.none { it.id !in next }) return false
        prefs(ctx).edit().putStringSet("disabledBackdrops", next).apply()
        return true
    }

    fun page(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val guidance = Form.body(ctx, "Choose the scenes used in battle. At least one must stay on.")
        guidance.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        addView(guidance)
        BackdropCatalog.ALL.groupBy { if (it.dark) "Dark scenes" else "Bright scenes" }.forEach { (title, scenes) ->
            addView(Form.sect(ctx, title))
            scenes.forEach { bg ->
                val image = Preview(ctx, bg)
                val sw = Switch(ctx).apply {
                    isChecked = bg.id !in disabled(ctx)
                    Ui.tintSwitch(this)
                    setOnCheckedChangeListener { _, on ->
                        if (!setEnabled(ctx, bg.id, on)) {
                            isChecked = true
                            guidance.text = "Keep at least one background on."
                        }
                    }
                }
                val row = Form.row(ctx, bg.id.replace('-', ' ').replaceFirstChar { it.titlecase() }, "Allow this scene to appear as a battle background", sw) { sw.toggle() }
                addView(Form.cardOf(ctx, listOf(image.apply { layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(ctx, 210)) }, row)), Form.lp(ctx, 8))
            }
        }
    }

    internal var previewPool: java.util.concurrent.Executor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "background-previews").apply { isDaemon = true }
    }

    /** Fixed bounds avoid scroll jumps; only visible previews request art, and offscreen bitmaps are released. */
    internal class Preview(ctx: Context, private val bg: Backdrop) : ImageView(ctx) {
        private var requested = false
        @Volatile private var generation = 0
        private val visible = Rect()
        private val update = android.view.ViewTreeObserver.OnPreDrawListener {
            refresh()
            true
        }
        init {
            contentDescription = bg.id.replace('-', ' ')
            scaleType = ScaleType.FIT_CENTER
        }
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            viewTreeObserver.addOnPreDrawListener(update)
        }
        override fun onDetachedFromWindow() {
            viewTreeObserver.removeOnPreDrawListener(update)
            clear()
            super.onDetachedFromWindow()
        }
        private fun clear() { generation++; requested = false; setImageDrawable(null) }
        internal fun refresh() {
            if (!getGlobalVisibleRect(visible)) {
                if (requested) clear()
                return
            }
            if (requested) return
            requested = true
            val token = ++generation
            // Bundled sharp art works offline. Drop queued work after scrolling away.
            val app = context.applicationContext
            previewPool.execute {
                if (token != generation) return@execute
                val thumb = runCatching { app.assets.open("backdrop-thumbs/${bg.id}.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()
                post { if (token == generation && drawable == null) setImageBitmap(thumb) }
            }

        }
    }
}
