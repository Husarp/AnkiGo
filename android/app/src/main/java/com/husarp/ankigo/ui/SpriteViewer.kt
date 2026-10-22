package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.gfx.SpriteView

// TICKET R10-B: the full-screen Pokémon viewer (tap the big sprite in the details).

/**
 * Full-screen sprite viewer: the big sprite on a dark scrim, animated when an animated one is cached, pinch-zoom + pan, double-tap
 * zoom, a "turn around" (front <-> back) button and a mirror button. Close with Back, the X, or a tap outside the sprite while not
 * zoomed. Over other apps (no Activity) the dialog is an overlay window.
 */
object SpriteViewer {
    /** The scrim: near-black, about 90 %, the same in the light and the dark theme. */
    internal const val SCRIM = 0xE6000000.toInt()

    /**
     * Opens the viewer for species [num] (the look number to draw, see `lookNum`), [shiny] or not. Never starts a download for
     * the animated front; the static picture may fetch its own PNG, and "turn around" may fetch the back sprite (as the battle does).
     */
    fun show(ctx: Context, num: Int, shiny: Boolean) {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val c = build(ctx, num, shiny) { dialog.dismiss() }
        dialog.setOnDismissListener { c.dispose() } // R10-FIX (Codex 2): a closed viewer takes its pending loads with it
        dialog.setContentView(c.root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(this, false)
            if (Ui.findActivity(ctx) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    /** Test seams: how the back sprite / the static front are fetched (the app's own downloader by default). */
    internal var fetchBack: (Context, com.husarp.ankigo.game.Species, Boolean, (java.io.File?) -> Unit) -> Unit =
        { c, s, shiny, cb -> Sprites.fetch(c, s, SpriteKind.BACK, shiny, cb) }
    internal var fetchStatic: (Context, Int, Boolean, (android.graphics.Bitmap?) -> Unit) -> Unit =
        { c, n, shiny, cb -> Sprites.fetchStatic(c, n, shiny, cb) }

    /** What [build] makes: the root plus the parts a screenshot test drives. */
    internal class Content(val root: FrameLayout, val zoom: SpriteZoomView, val turn: View, val mirror: View, val close: View) {
        /** Stops the loader: every pending fetch / decode that comes back later does nothing (no picture, no toast, no new load). */
        internal var dispose: () -> Unit = {}
            internal set
        /** True while the back picture (or the front turned round in its place) is shown. */
        var facingBack = false
            internal set
    }

    /** The viewer's content view (scrim, picture, buttons). [onClose] is the X; the tap outside the picture calls it too. */
    internal fun build(ctx: Context, num: Int, shiny: Boolean, onClose: () -> Unit): Content {
        val sp = GameRepo.data?.species?.get(num)
        val main = Handler(Looper.getMainLooper())
        val zoom = SpriteZoomView(ctx).apply {
            placeholderColor = Ui.typeColor(sp?.types?.firstOrNull() ?: Type.NORMAL)
            contentDescription = (sp?.name ?: "Pokémon") + ", picture. Pinch to zoom, double-tap to zoom in or out."
            this.onTapOutside = onClose
        }
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(SCRIM)
            addView(zoom, FrameLayout.LayoutParams(-1, -1))
        }
        val closeBtn = iconButton(ctx, R.drawable.ic_close, "Close")
        closeBtn.setOnClickListener { onClose() }
        val turnBtn = pill(ctx, R.drawable.ic_turn, "Turn around", "Turn around: front or back")
        val mirrorBtn = pill(ctx, R.drawable.ic_mirror, "Mirror", "Mirror: flip left and right")
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(turnBtn, LinearLayout.LayoutParams(-2, Ui.dp(ctx, 48)))
            addView(mirrorBtn, LinearLayout.LayoutParams(-2, Ui.dp(ctx, 48)).also { it.marginStart = Ui.dp(ctx, 12) })
        }
        val margin = Ui.dp(ctx, 16)
        val closeLp = FrameLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48), Gravity.TOP or Gravity.END).also { it.setMargins(margin, margin, margin, margin) }
        val barLp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.setMargins(margin, margin, margin, margin) }
        root.addView(closeBtn, closeLp)
        root.addView(bar, barLp)
        // keep the buttons clear of the status / navigation bar and the camera cut-out
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            closeLp.setMargins(margin + b.left, margin + b.top, margin + b.right, margin)
            barLp.setMargins(margin + b.left, margin, margin + b.right, margin + b.bottom)
            closeBtn.layoutParams = closeLp
            bar.layoutParams = barLp
            zoom.reserveTop = b.top + Ui.dp(ctx, 80); zoom.reserveBottom = b.bottom + Ui.dp(ctx, 80)
            ins
        }
        zoom.reserveTop = Ui.dp(ctx, 80); zoom.reserveBottom = Ui.dp(ctx, 80)

        val c = Content(root, zoom, turnBtn, mirrorBtn, closeBtn)
        val loader = Loader(ctx, sp, num, shiny, zoom, main)
        c.dispose = { loader.dispose() }
        // also when the window goes away without the dialog's own dismiss (the Activity closes under it)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { loader.dispose() }
        })
        // pinned: the effective mirror = the user's mirror xor "the front is shown turned in place of a missing back"
        var userMirror = false
        fun applyMirror() { zoom.mirror = userMirror != loader.backIsTurnedFront }
        loader.onChange = { applyMirror() }
        mirrorBtn.setOnClickListener { userMirror = !userMirror; applyMirror() }
        turnBtn.setOnClickListener {
            c.facingBack = !c.facingBack
            if (c.facingBack) loader.back() else loader.front()
        }
        loader.front()
        return c
    }

    private fun iconButton(ctx: Context, icon: Int, label: String): ImageView = ImageView(ctx).apply {
        setImageResource(icon)
        setColorFilter(Ui.c(ctx, R.color.ag_text), android.graphics.PorterDuff.Mode.SRC_IN)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val p = Ui.dp(ctx, 12)
        setPadding(p, p, p, p)
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx)
        contentDescription = label
        isClickable = true; isFocusable = true
    }

    private fun pill(ctx: Context, icon: Int, text: String, label: String): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(Ui.dp(ctx, 14), 0, Ui.dp(ctx, 16), 0)
        minimumHeight = Ui.dp(ctx, 48)
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx)
        contentDescription = label
        isClickable = true; isFocusable = true
        addView(ImageView(ctx).apply {
            setImageResource(icon)
            setColorFilter(Ui.c(ctx, R.color.ag_text), android.graphics.PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 22), Ui.dp(ctx, 22)))
        addView(Ui.text(ctx, 13f, 800, R.color.ag_text).apply { this.text = text; maxLines = 1 },
            LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 8) })
    }

    /**
     * Gets the pictures into [view]: the FRONT as the animated battle GIF when it is cached (the static picture meanwhile and when
     * it fails), else the static sprite; the BACK as the battle's back sprite (cached, else fetched like the battle does). A species
     * with no back picture shows its front turned round, like the battle's mirror-front fallback, with a short toast.
     * Only the newest request counts (a token), so a slow decode never overwrites a newer picture.
     */
    private class Loader(
        ctx: Context, private val sp: com.husarp.ankigo.game.Species?, private val num: Int, private val shiny: Boolean,
        view: SpriteZoomView, private val main: Handler,
    ) {
        // dropped on dispose: a late callback must not keep the closed viewer and its context alive
        private var ctxRef: Context? = ctx
        private var viewRef: SpriteZoomView? = view
        private val ctx get() = ctxRef!!
        private val view get() = viewRef!!
        var onChange: () -> Unit = {}
        /** True while the front is shown in place of a missing back (drawn mirrored). */
        var backIsTurnedFront = false
            private set
        private var token = Any()

        private fun fresh(): Any { val t = Any(); token = t; return t }

        /** Closing: no later callback passes its token check; the context and the view are let go. */
        fun dispose() {
            token = Any()
            onChange = {}
            ctxRef = null; viewRef = null
        }

        fun front() {
            if (viewRef == null) return
            val t = fresh()
            backIsTurnedFront = false
            onChange()
            val battle = sp?.let { Sprites.cached(ctx, it, SpriteKind.FRONT, shiny) }
            val staticNow = Sprites.staticBitmap(ctx, num, shiny)
            if (staticNow != null) view.setImage(SpriteImage(listOf(staticNow), intArrayOf(0))) else view.setImage(null)
            if (battle != null && battle.name.endsWith(".gif", ignoreCase = true)) {
                PanelSprites.pool.execute {
                    val img = Sprites.decode(battle)
                    main.post {
                        if (token !== t) return@post
                        if (img != null && img.animated) view.setImage(img) else if (staticNow == null) staticFallback(t, battle)
                    }
                }
                return
            }
            if (staticNow == null) staticFallback(t, battle)
        }

        /** No animated picture and no static one on disk: fetch the static PNG, else show the cached battle PNG, else the placeholder ball. */
        private fun staticFallback(t: Any, battle: java.io.File?) {
            fetchStatic(ctx, num, shiny) { bmp ->
                if (token !== t) return@fetchStatic
                if (bmp != null) { view.setImage(SpriteImage(listOf(bmp), intArrayOf(0))); return@fetchStatic }
                if (battle == null) return@fetchStatic
                PanelSprites.pool.execute {
                    val img = Sprites.decode(battle)
                    main.post { if (token === t && img != null) view.setImage(img) }
                }
            }
        }

        fun back() {
            if (viewRef == null) return
            val t = fresh()
            val s = sp
            if (s == null) { noBack(t); return }
            backIsTurnedFront = false
            onChange()
            fun decode(f: java.io.File) {
                PanelSprites.pool.execute {
                    val img = Sprites.decode(f)
                    main.post { if (token !== t) return@post; if (img != null) view.setImage(img) else noBack(t) }
                }
            }
            val cached = Sprites.cached(ctx, s, SpriteKind.BACK, shiny)
            if (cached != null) { decode(cached); return }
            fetchBack(ctx, s, shiny) { f ->
                if (token !== t) return@fetchBack
                if (f != null) decode(f) else noBack(t)
            }
        }

        private fun noBack(t: Any) {
            if (token !== t) return
            Ui.toast(ctx, "No back picture for this Pokémon: showing its front, turned round.")
            front()
            backIsTurnedFront = true
            onChange()
        }
    }
}

/**
 * The picture itself: centred, nearest-neighbour (smoothed only for pictures bigger than pixel art), animated while attached and
 * shown (no loop in reduced motion: the first frame), with pinch-zoom, drag-pan and double-tap zoom ([ViewerMath]).
 */
internal class SpriteZoomView(ctx: Context) : View(ctx) {
    var mirror = false
        set(v) { if (v != field) { field = v; invalidate() } }
    var placeholderColor = 0xFFA8A77A.toInt()
    /** A tap outside the picture while it is not zoomed. */
    var onTapOutside: (() -> Unit)? = null
    /** Room kept free at the top / bottom at zoom 1 (the buttons), so the picture does not sit under them. */
    var reserveTop = 0
        set(v) { field = v; relayout() }
    var reserveBottom = 0
        set(v) { field = v; relayout() }

    private var image: SpriteImage? = null
    private var frame = 0
    private var ticking = false
    internal var pose = ViewerMath.Pose.FIT
        private set
    private var scale = 0f // device px per picture pixel at zoom 1
    private var layout = ViewerMath.Layout(0f, 0f, 0f, 0f)
    // PLAN 205 (R14): a STILL picture floats softly (canvas transform, the feet as the pivot), same maths / period / height as SpriteView
    private var idleAnim: android.animation.ValueAnimator? = null
    private var idlePhase = 0f
    /** True while the idle float runs (for tests). */
    internal val idleRunning: Boolean get() = idleAnim != null
    /** How many times the idle float was started (for tests: a snapshot host detaches the view again afterwards). */
    internal var idleStarts = 0
        private set

    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val rect = RectF()
    private val placeholder by lazy { SpritePlaceholder(placeholderColor) }
    private val tick = Runnable {
        ticking = false
        val img = image ?: return@Runnable
        if (!img.animated) return@Runnable
        frame = (frame + 1) % img.frames.size
        invalidate()
        schedule()
    }

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            setPose(ViewerMath.pinch(pose, d.scaleFactor, d.focusX - width / 2f, d.focusY - height / 2f, layout))
            return true
        }
    }).apply { isQuickScaleEnabled = false }

    private val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (image == null) return true
            setPose(ViewerMath.doubleTap(pose, e.x - width / 2f, e.y - height / 2f, layout))
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaleDetector.isInProgress) return true
            setPose(ViewerMath.drag(pose, -dx, -dy, layout))
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (ViewerMath.closesOnTap(pose, e.x, e.y, layout)) onTapOutside?.invoke()
            return true
        }
    })

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Shows [img] (null = the placeholder ball) at fit size, centred; zoom and pan start over. */
    fun setImage(img: SpriteImage?) {
        image = img
        frame = 0
        pose = ViewerMath.Pose.FIT
        removeCallbacks(tick); ticking = false
        relayout()
        updateAnimation()
    }

    /** Sets the zoom and pan (clamped); for the double-tap, a screenshot test and a restored state. */
    fun setPose(p: ViewerMath.Pose) {
        val c = ViewerMath.clamp(p, layout)
        if (c != pose) { pose = c; invalidate() }
    }

    /** The picture's rectangle on screen (left, top, right, bottom in this view's coordinates); empty before the first layout. */
    fun spriteRect(): FloatArray = ViewerMath.spriteRect(pose, layout)

    /** The height the picture may take at zoom 1: the box minus the same reserve above and below, so it stays centred. */
    private fun freeHeight(): Int = (height - 2 * maxOf(reserveTop, reserveBottom)).coerceAtLeast(1)

    private fun relayout() {
        val img = image
        val freeH = freeHeight()
        scale = if (img == null) 0f else ViewerMath.fitScale(img.width, img.height, (width - 2 * Ui.dp(context, 16)).coerceAtLeast(1), freeH)
        layout = if (img == null) ViewerMath.Layout(0f, 0f, width.toFloat(), height.toFloat())
        else ViewerMath.Layout(img.width * scale, img.height * scale, width.toFloat(), height.toFloat())
        pose = ViewerMath.clamp(pose, layout)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    override fun onDraw(canvas: Canvas) {
        val img = image
        if (img == null) {
            val d = minOf(width, freeHeight()) * 0.5f
            val cx = width / 2f; val cy = height / 2f
            placeholder.setBounds((cx - d).toInt(), (cy - d).toInt(), (cx + d).toInt(), (cy + d).toInt())
            placeholder.draw(canvas)
            return
        }
        if (scale <= 0f) return
        val bmp = img.frames[frame.coerceIn(0, img.frames.size - 1)]
        val r = ViewerMath.spriteRect(pose, layout)
        rect.set(r[0], r[1], r[2], r[3])
        paint.isFilterBitmap = SpriteView.SMOOTH
        val save = canvas.save()
        if (idleAnim != null && idlePhase > 0f) {
            canvas.translate(0f, MonPreview.idleOffsetPx(idlePhase, resources.displayMetrics.density))
            canvas.scale(1f, SpriteView.idleBreath(idlePhase), rect.centerX(), rect.bottom)
        }
        if (mirror) canvas.scale(-1f, 1f, rect.centerX(), rect.centerY())
        canvas.drawBitmap(bmp, null, rect, paint)
        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        taps.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    // ---- animation: runs only while attached and shown, and not in reduced motion ----

    private fun schedule() {
        val img = image ?: return
        if (ticking) return
        ticking = true
        postDelayed(tick, MonPreview.durations(img.delays, img.frames.size)[frame.coerceIn(0, img.frames.size - 1)].toLong())
    }

    private fun updateAnimation() {
        val img = image
        val run = android.animation.ValueAnimator.areAnimatorsEnabled() && !context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false) &&
            img != null && img.animated && isAttachedToWindow && windowVisibility == VISIBLE && isShown
        if (run) schedule() else { removeCallbacks(tick); ticking = false }
        updateIdle()
    }

    /** PLAN 205: starts or stops the idle float by [SpriteView.idleShouldRun]. */
    private fun updateIdle(detaching: Boolean = false) {
        val img = image
        val run = SpriteView.idleShouldRun(true, img != null, img != null && img.animated,
            android.animation.ValueAnimator.areAnimatorsEnabled(),
            context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false),
            !detaching && isAttachedToWindow && windowVisibility == VISIBLE && isShown)
        if (run) {
            if (idleAnim != null) return
            idleStarts++
            idleAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SpriteView.IDLE_PERIOD_MS
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { idlePhase = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            idleAnim?.let { it.removeAllUpdateListeners(); it.cancel() }
            idleAnim = null
            if (idlePhase != 0f) { idlePhase = 0f; invalidate() }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { removeCallbacks(tick); ticking = false; super.onDetachedFromWindow(); updateIdle(detaching = true) }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }
}
