package com.geotagcamera.geotagginglocationonphoto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Query(
        """
        SELECT p.id, p.name, p.createdAtEpochMs, p.archived, COUNT(ph.id) AS photoCount
        FROM projects p LEFT JOIN photos ph ON ph.projectId = p.id
        GROUP BY p.id
        ORDER BY p.archived ASC, p.createdAtEpochMs DESC
        """
    )
    fun observeAll(): Flow<List<ProjectWithCount>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: Long): Flow<ProjectEntity?>

    @Query("SELECT COUNT(*) FROM projects WHERE name = :name COLLATE NOCASE AND id != :exceptId")
    suspend fun countWithName(name: String, exceptId: Long = 0): Int

    @Query("UPDATE projects SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE projects SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("UPDATE photos SET projectId = NULL WHERE projectId = :id")
    suspend fun ungroupPhotos(id: Long)

    /** Deletes the project only; its photos stay in the gallery, just ungrouped. */
    @Transaction
    suspend fun delete(id: Long) {
        ungroupPhotos(id)
        deleteRow(id)
    }
}
