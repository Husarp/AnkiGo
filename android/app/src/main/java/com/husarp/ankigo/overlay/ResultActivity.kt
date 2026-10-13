package com.husarp.ankigo.overlay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.husarp.ankigo.Report

/**
 * Invisible Activity that runs a "for result" intent (file picker) on behalf of a screen in a floating card, which has no
 * Activity of its own. Use [launch]; the callback gets (resultCode, data) once on the main thread.
 */
class ResultActivity : Activity() {
    private var id = -1
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getIntExtra(EXTRA_ID, -1)
        if (savedInstanceState != null) return // recreated while the picker is open: its result is still on its way
        val target = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_TARGET, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(EXTRA_TARGET)
        }
        if (target == null) { deliver(RESULT_CANCELED, null); return }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(target, REQUEST)
        } catch (e: ActivityNotFoundException) {
            Report.fail("overlay", "no picker for ${target.action}", e)
            deliver(RESULT_CANCELED, null)
        } catch (e: Exception) {
            Report.fail("overlay", "picker start failed", e)
            deliver(RESULT_CANCELED, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST) deliver(resultCode, data)
    }

    override fun onDestroy() {
        // Only a real end counts as "no result" (a rotation / split-screen resize recreates us and the result still comes).
        if (isFinishing) deliver(RESULT_CANCELED, null)
        super.onDestroy()
    }

    /** Hands the result to the caller once, then closes. The callback runs first so a read grant on the data is still valid. */
    private fun deliver(code: Int, data: Intent?) {
        if (delivered) return
        delivered = true
        val cb = callbacks.remove(id)
        try {
            cb?.invoke(code, data)
        } catch (e: Exception) {
            Report.fail("overlay", "picker callback failed", e)
        }
        if (!isFinishing) finish()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TARGET = "target"
        private const val REQUEST = 1

        // Main thread only. Process-wide so a result survives the Activity being recreated.
        private val callbacks = HashMap<Int, (Int, Intent?) -> Unit>()
        private var nextId = 1

        /** Starts [target] for a result from a context without an Activity; [onResult] gets (resultCode, data) once, on the main thread. */
        fun launch(ctx: Context, target: Intent, onResult: (Int, Intent?) -> Unit) {
            val id = nextId++
            callbacks[id] = onResult
            val i = Intent(ctx.applicationContext, ResultActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TARGET, target)
            try {
                ctx.applicationContext.startActivity(i)
            } catch (e: Exception) {
                callbacks.remove(id)
                throw e
            }
        }
    }
}
