package com.egrmeister.watertiles.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterEntryDao {
    @Query(
        "SELECT * FROM water_entries WHERE loggedLocalDate = :date " +
            "ORDER BY loggedLocalTime DESC, timestamp DESC, id DESC",
    )
    fun observeForDate(date: String): Flow<List<WaterEntryEntity>>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE loggedLocalDate = :date")
    fun observeTotalForDate(date: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_entries WHERE loggedLocalDate = :date")
    suspend fun totalForDate(date: String): Long

    @Query(
        "SELECT loggedLocalDate AS date, SUM(amountMl) AS total FROM water_entries " +
            "WHERE loggedLocalDate BETWEEN :from AND :to GROUP BY loggedLocalDate",
    )
    fun observeDailyTotals(from: String, to: String): Flow<List<DailyTotalRow>>

    @Query("SELECT MAX(timestamp) FROM water_entries")
    suspend fun latestTimestamp(): Long?

    @Query("SELECT * FROM water_entries WHERE id = :id")
    suspend fun getById(id: Long): WaterEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WaterEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: WaterEntryEntity): Long

    @Update
    suspend fun update(entity: WaterEntryEntity): Int

    @Delete
    suspend fun delete(entity: WaterEntryEntity): Int

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM water_entries")
    suspend fun deleteAll()
}

@Dao
interface GoalChangeDao {
    @Query("SELECT * FROM goal_changes ORDER BY effectiveLocalDate ASC")
    fun observeAll(): Flow<List<GoalChangeEntity>>

    @Query("SELECT * FROM goal_changes ORDER BY effectiveLocalDate ASC")
    suspend fun getAll(): List<GoalChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GoalChangeEntity)

    @Query("DELETE FROM goal_changes WHERE effectiveLocalDate > :date")
    suspend fun deleteAfter(date: String)

    @Query("DELETE FROM goal_changes")
    suspend fun deleteAll()
}
