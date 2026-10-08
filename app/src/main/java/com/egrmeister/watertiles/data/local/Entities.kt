package com.egrmeister.watertiles.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** loggedLocalDate is ISO yyyy-MM-dd; loggedLocalTime is ISO HH:mm:ss (both sort lexicographically). */
@Entity(
    tableName = "water_entries",
    indices = [
        Index(value = ["loggedLocalDate", "loggedLocalTime"]),
        Index(value = ["timestamp"]),
    ],
)
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amountMl") val amountMl: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "loggedLocalDate") val loggedLocalDate: String,
    @ColumnInfo(name = "loggedLocalTime") val loggedLocalTime: String,
    @ColumnInfo(name = "zoneId") val zoneId: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
)

@Entity(tableName = "goal_changes")
data class GoalChangeEntity(
    @PrimaryKey @ColumnInfo(name = "effectiveLocalDate") val effectiveLocalDate: String,
    @ColumnInfo(name = "goalMl") val goalMl: Int,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
)

data class DailyTotalRow(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "total") val total: Long,
)
