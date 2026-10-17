package com.husarp.ankigo.help

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.husarp.ankigo.Anki
import com.husarp.ankigo.Media
import com.husarp.ankigo.Report
import java.io.File

/** How AnkiDroid was built: "full" (GitHub / F-Droid, asks for All files access) or "play" (Play Store, files stay private). */
enum class AnkiDroidFlavour { FULL, PLAY, UNKNOWN }

/** What AnkiGo sees at AnkiDroid's public pictures folder (/storage/emulated/0/AnkiDroid/collection.media). */
enum class PublicFolder {
    /** The folder is there and its files can be listed. */
    READABLE,
    /** The folder is there, but AnkiGo may not list it (no All files access). */
    BLOCKED,
    /** Nothing at that path (or Android hides it). */
    NOT_FOUND,
}

/**
 * Everything the "Set up pictures" checks need, as plain values (PLAN 41), so [PictureSetup.evaluate] is pure and tested.
 * [PictureFacts.read] (PictureFacts.kt) fills it from the phone.
 *
 * Sample = the picture file names AnkiDroid's API gives for the next cards of the selected deck ([sampleTotal] distinct
 * names), [sampleReadable] of them could be opened by AnkiGo (any source: AnkiDroid's folder, a picked folder, an imported copy).
 */
data class PictureFacts(
    val androidSdk: Int,
    val ankiDroidInstalled: Boolean,
    val flavour: AnkiDroidFlavour,
    val publicFolder: PublicFolder,
    val allFilesAccess: Boolean,
    /** AnkiGo has a picked media folder or an imported copy (so it can show pictures without reading AnkiDroid's folder). */
    val ownSource: Boolean,
    /** AnkiGo may ask AnkiDroid for cards. When false the sample could not be taken. */
    val apiAllowed: Boolean,
    val sampleTotal: Int,
    val sampleReadable: Int,
    /** [ownSource] comes (at least partly) from a folder the user picked, which can show new pictures; false = only an imported copy. */
    val pickedFolder: Boolean = false,
) {
    companion object {
        private const val TAG = "PICS"
        private const val MANAGE_ALL_FILES = "android.permission.MANAGE_EXTERNAL_STORAGE"

        /**
         * Reads the facts from the phone. SLOW and blocking (file listing, content-provider queries for up to
         * [PictureSetup.SAMPLE_CARDS] cards, one file lookup per picture): call it OFF the main thread only.
         * Never throws; whatever can't be read becomes the "unknown" value.
         */
        fun read(ctx: Context): PictureFacts {
            val installed = try { Anki.installed(ctx).firstOrNull { it.pkg == Anki.pkg } } catch (_: Exception) { null }
            val flavour = if (installed == null) AnkiDroidFlavour.UNKNOWN else flavourOf(ctx, Anki.pkg)
            val folder = publicFolder()
            val allFiles = Media.allFilesAccess()
            val picked = try { Media.treeUri(ctx) != null } catch (_: Exception) { false }
            val own = try { picked || (Media.ownDir(ctx).list()?.isNotEmpty() == true) } catch (_: Exception) { false }
            val api = try { Anki.hasPermission(ctx) } catch (_: Exception) { false }
            var total = 0
            var readable = 0
            if (api) {
                try {
                    val names = sampleNames(ctx)
                    total = names.size
                    readable = names.count { Media.find(ctx, it) != null }
                } catch (e: Exception) {
                    Report.fail(TAG, "picture sample", e)
                }
            }
            return PictureFacts(Build.VERSION.SDK_INT, installed != null, flavour, folder, allFiles, own, api, total, readable, picked)
        }

        /** FULL when the package asks for All files access (the GitHub / F-Droid builds), PLAY when it doesn't. */
        private fun flavourOf(ctx: Context, pkg: String): AnkiDroidFlavour = try {
            @Suppress("DEPRECATION")
            val perms = ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions
            if (perms?.contains(MANAGE_ALL_FILES) == true) AnkiDroidFlavour.FULL else AnkiDroidFlavour.PLAY
        } catch (_: Exception) {
            AnkiDroidFlavour.UNKNOWN
        }

        private fun publicFolder(): PublicFolder = try {
            val dir = File(Environment.getExternalStorageDirectory(), "AnkiDroid/collection.media")
            when {
                dir.list() != null -> PublicFolder.READABLE
                dir.exists() -> PublicFolder.BLOCKED
                else -> PublicFolder.NOT_FOUND
            }
        } catch (_: Exception) {
            PublicFolder.NOT_FOUND
        }

        /** Distinct picture file names AnkiDroid lists for the next [PictureSetup.SAMPLE_CARDS] cards of the selected deck. */
        private fun sampleNames(ctx: Context): List<String> {
            val deck = Anki.selectedDeckInfo(ctx) ?: return emptyList()
            return Anki.nextCards(ctx, deck.id, PictureSetup.SAMPLE_CARDS)
                .flatMap { it.media }
                .filter { PictureSetup.isPicture(it) }
                .distinct()
        }
    }
}
