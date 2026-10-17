package com.husarp.ankigo.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureSetupTest {
    /** Everything fine: full build, public folder readable, All files access, 10 of 10 pictures. */
    private val good = PictureFacts(
        androidSdk = 34, ankiDroidInstalled = true, flavour = AnkiDroidFlavour.FULL, publicFolder = PublicFolder.READABLE,
        allFilesAccess = true, ownSource = false, apiAllowed = true, sampleTotal = 10, sampleReadable = 10,
    )

    private fun step(f: PictureFacts, id: StepId) = PictureSetup.evaluate(f).first { it.id == id }
    private fun actions(s: SetupStep) = s.fixes.map { it.action }

    @Test fun everythingOkGivesAllGreenAndNoFixes() {
        val steps = PictureSetup.evaluate(good)
        assertEquals(StepId.entries, steps.map { it.id })
        assertTrue(steps.all { it.status == StepStatus.OK })
        assertTrue(steps.all { it.fixes.isEmpty() })
        assertEquals(StepStatus.OK, PictureSetup.overall(steps))
    }

    @Test fun playBuildFailsWithGithubHelpAndApkgFixes() {
        val f = good.copy(flavour = AnkiDroidFlavour.PLAY, publicFolder = PublicFolder.NOT_FOUND, sampleReadable = 0)
        val build = step(f, StepId.BUILD)
        assertEquals(StepStatus.FAIL, build.status)
        assertEquals(listOf(FixAction.OPEN_GITHUB_RELEASES, FixAction.HELP_PAGE, FixAction.IMPORT_APKG), actions(build))
        assertEquals(HelpContent.PICTURES_DONT_SHOW, build.fixes[1].helpPageId)
        val folder = step(f, StepId.FOLDER)
        assertEquals(StepStatus.FAIL, folder.status)
        assertTrue(folder.text.contains("Android/data"))
        assertEquals(StepStatus.FAIL, step(f, StepId.SAMPLE).status)
        assertEquals(StepStatus.UNKNOWN, step(f, StepId.MISSING).status) // not a download problem
        assertEquals(StepStatus.FAIL, PictureSetup.overall(PictureSetup.evaluate(f)))
    }

    @Test fun fullBuildStillInAndroidDataPointsToMovingTheFolder() {
        val f = good.copy(publicFolder = PublicFolder.NOT_FOUND, sampleReadable = 0)
        val folder = step(f, StepId.FOLDER)
        assertEquals(StepStatus.FAIL, folder.status)
        assertEquals(listOf(FixAction.OPEN_ANKIDROID_SETTINGS, FixAction.HELP_PAGE, FixAction.IMPORT_APKG), actions(folder))
        assertEquals(HelpContent.MOVE_COLLECTION, folder.fixes[1].helpPageId)
        assertTrue(folder.text.contains("doesn't move"))
    }

    @Test fun fullBuildInPublicFolderWithoutAllFilesAccessAsksForIt() {
        val f = good.copy(publicFolder = PublicFolder.BLOCKED, allFilesAccess = false, sampleReadable = 0)
        assertEquals(StepStatus.FAIL, step(f, StepId.FOLDER).status)
        assertEquals(listOf(FixAction.OPEN_ALL_FILES_ACCESS), actions(step(f, StepId.FOLDER)))
        val access = step(f, StepId.ALL_FILES)
        assertEquals(StepStatus.FAIL, access.status)
        assertEquals(listOf(FixAction.OPEN_ALL_FILES_ACCESS), actions(access))
        assertTrue(actions(step(f, StepId.SAMPLE)).contains(FixAction.OPEN_ALL_FILES_ACCESS))
    }

    @Test fun fullBuildNotSeeingTheFolderWithoutAccessIsUnknownNotFail() {
        val f = good.copy(publicFolder = PublicFolder.NOT_FOUND, allFilesAccess = false, sampleReadable = 0)
        val folder = step(f, StepId.FOLDER)
        assertEquals(StepStatus.UNKNOWN, folder.status)
        assertEquals(listOf(FixAction.OPEN_ALL_FILES_ACCESS), actions(folder))
    }

    @Test fun allFilesAccessWithAnOwnCopyIsOnlyAWarning() {
        val f = good.copy(allFilesAccess = false, ownSource = true)
        assertEquals(StepStatus.WARN, step(f, StepId.ALL_FILES).status)
    }

    @Test fun aVerifiedImportedCopyOnAPlayBuildIsNoFailure() {
        val f = good.copy(flavour = AnkiDroidFlavour.PLAY, publicFolder = PublicFolder.NOT_FOUND, allFilesAccess = false, ownSource = true)
        assertEquals(StepStatus.WARN, step(f, StepId.BUILD).status)
        assertEquals(StepStatus.WARN, step(f, StepId.FOLDER).status)
        assertTrue(step(f, StepId.FOLDER).text.contains("imported"))
        assertEquals(StepStatus.OK, step(f, StepId.SAMPLE).status)
        assertTrue(PictureSetup.evaluate(f).none { it.status == StepStatus.FAIL })
        // nothing readable from the own source: still the old failures
        val bad = f.copy(sampleReadable = 0)
        assertEquals(StepStatus.FAIL, step(bad, StepId.BUILD).status)
        assertEquals(StepStatus.FAIL, step(bad, StepId.FOLDER).status)
    }

    @Test fun aWorkingPickedFolderIsFineAndNeedsNoAllFilesAccess() {
        val f = good.copy(flavour = AnkiDroidFlavour.PLAY, publicFolder = PublicFolder.NOT_FOUND, allFilesAccess = false, ownSource = true, pickedFolder = true)
        val steps = PictureSetup.evaluate(f)
        assertTrue(steps.all { it.status == StepStatus.OK })
        assertFalse(step(f, StepId.ALL_FILES).text.contains("won't show"))
    }

    @Test fun missingPicturesAreReportedEvenWhenNoneWereReadable() {
        val s = step(good.copy(sampleTotal = 10, sampleReadable = 0), StepId.MISSING)
        assertEquals(StepStatus.FAIL, s.status)
        assertTrue(s.text.contains("hasn't downloaded 10 pictures"))
        // the folder can't be listed: still not a download problem
        assertEquals(StepStatus.UNKNOWN, step(good.copy(publicFolder = PublicFolder.BLOCKED, sampleReadable = 0), StepId.MISSING).status)
    }

    @Test fun oldAndroidDoesNotNeedAllFilesAccess() {
        val f = good.copy(androidSdk = 28, allFilesAccess = false)
        assertEquals(StepStatus.OK, step(f, StepId.ALL_FILES).status)
        assertFalse(actions(step(f.copy(sampleReadable = 0), StepId.SAMPLE)).contains(FixAction.OPEN_ALL_FILES_ACCESS))
    }

    @Test fun manyMissingPicturesFailsWithTheDownloadFix() {
        val f = good.copy(sampleTotal = 40, sampleReadable = 10)
        val s = step(f, StepId.MISSING)
        assertEquals(StepStatus.FAIL, s.status)
        assertTrue(s.text.contains("AnkiDroid hasn't downloaded 30 pictures"))
        assertTrue(s.text.contains("600") && s.text.contains("Download") && s.text.contains("open"))
        assertEquals(listOf(FixAction.OPEN_ANKIDROID, FixAction.HELP_PAGE), actions(s))
        assertEquals(HelpContent.PICTURES_MISSING, s.fixes[1].helpPageId)
    }

    @Test fun fewMissingPicturesIsOnlyAWarningAndSingularIsRight() {
        val few = step(good.copy(sampleTotal = 50, sampleReadable = 48), StepId.MISSING)
        assertEquals(StepStatus.WARN, few.status)
        assertTrue(few.text.contains("hasn't downloaded 2 pictures"))
        assertTrue(step(good.copy(sampleTotal = 50, sampleReadable = 49), StepId.MISSING).text.contains("1 picture "))
    }

    @Test fun missingThresholdBoundary() {
        assertFalse(PictureSetup.manyMissing(2, 10))
        assertTrue(PictureSetup.manyMissing(3, 10))
        assertFalse(PictureSetup.manyMissing(3, 50)) // 6% of the sample
        assertTrue(PictureSetup.manyMissing(5, 50))
        assertFalse(PictureSetup.manyMissing(0, 0))
    }

    @Test fun noApiPermissionOrNoPicturesLeavesTheSampleStepsUnknown() {
        for (f in listOf(good.copy(apiAllowed = false, sampleTotal = 0, sampleReadable = 0), good.copy(sampleTotal = 0, sampleReadable = 0))) {
            assertEquals(StepStatus.UNKNOWN, step(f, StepId.SAMPLE).status)
            assertEquals(StepStatus.UNKNOWN, step(f, StepId.MISSING).status)
        }
        assertEquals(StepStatus.UNKNOWN, PictureSetup.overall(PictureSetup.evaluate(good.copy(apiAllowed = false, sampleTotal = 0, sampleReadable = 0))))
    }

    @Test fun ankiDroidMissingFailsWithTheGithubFix() {
        val f = good.copy(ankiDroidInstalled = false, flavour = AnkiDroidFlavour.UNKNOWN, publicFolder = PublicFolder.NOT_FOUND, sampleTotal = 0, sampleReadable = 0)
        val s = step(f, StepId.ANKIDROID)
        assertEquals(StepStatus.FAIL, s.status)
        assertEquals(listOf(FixAction.OPEN_GITHUB_RELEASES), actions(s))
        assertEquals(StepStatus.UNKNOWN, step(f, StepId.BUILD).status)
    }

    @Test fun everyHelpFixPointsAtARealPage() {
        val cases = listOf(
            good.copy(flavour = AnkiDroidFlavour.PLAY, publicFolder = PublicFolder.NOT_FOUND, sampleReadable = 0),
            good.copy(publicFolder = PublicFolder.NOT_FOUND, sampleReadable = 0),
            good.copy(sampleTotal = 40, sampleReadable = 10),
            good.copy(flavour = AnkiDroidFlavour.UNKNOWN),
        )
        for (f in cases) for (s in PictureSetup.evaluate(f)) for (fix in s.fixes) {
            if (fix.action == FixAction.HELP_PAGE) assertNotNull(fix.helpPageId, HelpContent.page(fix.helpPageId!!))
            else assertEquals(null, fix.helpPageId)
        }
    }

    @Test fun everyStepHasTitleAndText() {
        for (f in listOf(good, good.copy(flavour = AnkiDroidFlavour.PLAY, publicFolder = PublicFolder.NOT_FOUND), good.copy(ankiDroidInstalled = false))) {
            assertTrue(PictureSetup.evaluate(f).all { it.title.isNotBlank() && it.text.isNotBlank() })
        }
    }

    @Test fun isPictureByExtension() {
        assertTrue(PictureSetup.isPicture("cat.PNG"))
        assertTrue(PictureSetup.isPicture("a b.jpeg"))
        assertFalse(PictureSetup.isPicture("sound.mp3"))
        assertFalse(PictureSetup.isPicture("noext"))
    }

    @Test fun diagnosticLinesSayBuildFolderAndWhatCanBeRead() {
        val ok = PictureSetup.diagnosticLines(good)
        assertEquals(3, ok.size)
        assertTrue(ok[0].contains("full"))
        assertTrue(ok[1].contains("/storage/emulated/0/AnkiDroid") && ok[1].contains("readable"))
        assertTrue(ok[2].contains("All files access=yes") && ok[2].contains("media folder=found") && ok[2].contains("10/10 readable, 0 missing"))
        val play = PictureSetup.diagnosticLines(good.copy(flavour = AnkiDroidFlavour.PLAY, publicFolder = PublicFolder.NOT_FOUND, allFilesAccess = false, sampleReadable = 0))
        assertTrue(play[0].contains("Play Store") && play[1].contains("Android/data"))
        assertTrue(play[2].contains("All files access=no") && play[2].contains("0/10 readable, 10 missing"))
        assertTrue(PictureSetup.diagnosticLines(good.copy(apiAllowed = false))[2].contains("no AnkiDroid permission"))
        assertTrue(PictureSetup.diagnosticLines(good.copy(ankiDroidInstalled = false))[0].contains("not installed"))
    }
}
