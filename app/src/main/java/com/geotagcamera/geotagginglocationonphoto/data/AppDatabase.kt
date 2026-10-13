package com.geotagcamera.geotagginglocationonphoto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PhotoEntity::class, GeoCacheEntity::class, ProjectEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun geoCacheDao(): GeoCacheDao
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** 1.3 -> 1.4: projects, plus project/note/place columns on photos. Existing photos keep everything. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `archived` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `projectId` INTEGER")
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `note` TEXT")
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `placeName` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_projectId` ON `photos` (`projectId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geotagcamera.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
