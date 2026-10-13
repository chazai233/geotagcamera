package com.geotagcamera.geotagginglocationonphoto.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A job, site or assignment that photos are grouped under ("Bridge 14
 * inspection", "Ward 7 survey"). Photos point at a project; a project never
 * owns the files, so deleting one only un-groups its photos.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAtEpochMs: Long,
    /** Archived projects drop out of the capture picker but keep their photos and reports. */
    val archived: Boolean = false
)

/** A project plus how many photos it has, for pickers and lists. */
data class ProjectWithCount(
    val id: Long,
    val name: String,
    val createdAtEpochMs: Long,
    val archived: Boolean,
    val photoCount: Int
)
