package com.husarp.ankigo.shots

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Bundle
import app.cash.paparazzi.Paparazzi
import com.android.layoutlib.bridge.android.BridgePackageManager

/**
 * Package manager for the Study / first-run / update screens: AnkiDroid is installed (or not) at [ankiVersion],
 * and AnkiGo itself reports [appVersion]. Everything else is layoutlib's own (empty) package manager.
 */
class FakePm(ctx: Context, private val ankiVersion: String?, private val appVersion: String) : BridgePackageManager(ctx) {
    override fun getPackageInfo(packageName: String, flags: Int): PackageInfo {
        val v = when (packageName) {
            "com.husarp.ankigo" -> appVersion
            "com.ichi2.anki" -> ankiVersion ?: throw PackageManager.NameNotFoundException(packageName)
            else -> throw PackageManager.NameNotFoundException(packageName)
        }
        return PackageInfo().apply { this.packageName = packageName; versionName = v; longVersionCode = 20240000L }
    }

    override fun resolveContentProvider(authority: String, flags: Int): ProviderInfo? =
        if (authority == "com.ichi2.anki.flashcards" && ankiVersion != null) ProviderInfo().apply {
            this.authority = authority; metaData = Bundle().apply { putInt("com.ichi2.anki.provider.spec", 2) }
        } else null
}

/** The render context with the fake package manager; [granted] = AnkiGo may use AnkiDroid's provider. */
class ShotCtx(base: Context, ankiVersion: String? = "2.24.0", appVersion: String = "0.4.2", private val granted: Boolean = true) : ContextWrapper(base) {
    private val pm = FakePm(base, ankiVersion, appVersion)
    override fun getPackageManager(): PackageManager = pm
    override fun checkSelfPermission(permission: String): Int =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
}

/** Sets a private field (declared in [owner] or a superclass) by reflection. */
fun setField(owner: Any, name: String, value: Any?) {
    var c: Class<*>? = owner.javaClass
    while (c != null) {
        try { c.getDeclaredField(name).apply { isAccessible = true }.set(owner, value); return } catch (_: NoSuchFieldException) { c = c.superclass }
    }
    error("no field $name")
}

fun getField(owner: Any, name: String): Any? {
    var c: Class<*>? = owner.javaClass
    while (c != null) {
        try { return c.getDeclaredField(name).apply { isAccessible = true }.get(owner) } catch (_: NoSuchFieldException) { c = c.superclass }
    }
    error("no field $name")
}

/** Calls a private method by name (first with that many params). */
fun call(owner: Any, name: String, vararg args: Any?): Any? {
    var c: Class<*>? = owner.javaClass
    while (c != null) {
        val m = c.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }
        if (m != null) { m.isAccessible = true; return try { m.invoke(owner, *args) } catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException } }
        c = c.superclass
    }
    error("no method $name")
}

/** Like [shoot], but sets the FakeGame scenario / sprite switch before every install (they reset after each one). */
fun Paparazzi.shootWith(
    name: String,
    sizes: List<Size> = listOf(Size.PHONE, Size.SPLIT),
    scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME,
    withSprites: Boolean = true,
    build: (Context) -> android.view.View,
) {
    for (size in sizes) for (theme in Theme.entries) {
        unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        FakeGame.scenario = scenario
        FakeGame.withSprites = withSprites
        val ctx = Shots.files(context)
        FakeGame.install(ctx)
        val v = build(ctx)
        val frame = android.widget.FrameLayout(ctx).apply {
            addView(v, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
    }
}
