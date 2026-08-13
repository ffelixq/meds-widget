package io.github.ffelixq.medswidget.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SensitiveExportCleanupTest {
    private lateinit var context: Context
    private lateinit var exportDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        exportDirectory.listFiles()?.forEach(File::delete)
    }

    @After
    fun tearDown() {
        exportDirectory.listFiles()?.forEach(File::delete)
    }

    @Test
    fun staleManagedExportsAreDeletedButUnrelatedFilesRemain() {
        val managed = File(exportDirectory, "meds-widget-2026-08-12-test.csv").apply { writeText("sensitive") }
        val unrelated = File(exportDirectory, "keep.txt").apply { writeText("keep") }
        managed.setLastModified(1_000L)
        unrelated.setLastModified(1_000L)

        SensitiveExportCleanup.cleanupStale(
            context,
            nowMillis = 1_000L + SensitiveExportCleanup.STALE_AFTER_MILLIS,
        )

        assertFalse(managed.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun pathTraversalNameCannotDeleteOutsideExportDirectory() {
        val outside = File(context.cacheDir, "meds-widget-outside.csv").apply { writeText("keep") }

        SensitiveExportCleanup.deleteManagedExport(context, "../${outside.name}")

        assertTrue(outside.exists())
        outside.delete()
    }
}
