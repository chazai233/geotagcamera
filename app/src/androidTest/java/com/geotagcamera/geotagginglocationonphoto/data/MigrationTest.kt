package com.geotagcamera.geotagginglocationonphoto.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A 1.3 database (schema 1) must open as schema 2 with every existing photo intact. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2_keepsPhotos() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO photos (filePath, capturedAtEpochMs, latitude, longitude, altitudeMeters, accuracyMeters, " +
                    "bearingDegrees, address, addressFromCache, orgLabel, sha256Hash, signatureBase64, signingKeyAlias, fieldWorkerSignature) " +
                    "VALUES ('content://x/1', 1000, 22.8, 87.9, NULL, 5.0, NULL, 'Addr', 0, NULL, 'abc', 'sig', 'k', 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, AppDatabase.MIGRATION_1_2)
        db.query("SELECT filePath, sha256Hash, projectId, note FROM photos").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("content://x/1", c.getString(0))
            assertEquals("abc", c.getString(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
        }
        db.query("SELECT COUNT(*) FROM projects").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }

    private companion object { const val DB = "migration-test.db" }
}
