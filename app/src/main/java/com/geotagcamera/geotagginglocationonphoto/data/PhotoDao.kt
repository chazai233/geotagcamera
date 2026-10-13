package com.geotagcamera.geotagginglocationonphoto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Query("SELECT * FROM photos ORDER BY capturedAtEpochMs DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT filePath FROM photos ORDER BY capturedAtEpochMs DESC LIMIT 1")
    fun observeLatestPath(): Flow<String?>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()

    @Query("SELECT * FROM photos WHERE projectId = :projectId ORDER BY capturedAtEpochMs ASC")
    suspend fun getForProject(projectId: Long): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id IN (:ids) ORDER BY capturedAtEpochMs ASC")
    suspend fun getByIds(ids: List<Long>): List<PhotoEntity>

    @Query("UPDATE photos SET projectId = :projectId WHERE id IN (:ids)")
    suspend fun moveToProject(ids: List<Long>, projectId: Long?)
}
