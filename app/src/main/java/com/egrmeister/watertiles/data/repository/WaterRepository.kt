package com.egrmeister.watertiles.data.repository

import com.egrmeister.watertiles.core.TimeProvider
import com.egrmeister.watertiles.data.local.GoalChangeDao
import com.egrmeister.watertiles.data.local.GoalChangeEntity
import com.egrmeister.watertiles.data.local.TransactionRunner
import com.egrmeister.watertiles.data.local.WaterEntryDao
import com.egrmeister.watertiles.data.local.WaterEntryEntity
import com.egrmeister.watertiles.domain.entries.WaterEntry
import com.egrmeister.watertiles.domain.goals.GoalChange
import com.egrmeister.watertiles.domain.goals.GoalResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class WaterRepository(
    private val entries: WaterEntryDao,
    private val goals: GoalChangeDao,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    // ---- entries -------------------------------------------------------------------------

    fun observeEntries(date: LocalDate): Flow<List<WaterEntry>> =
        entries.observeForDate(date.toString()).map { list -> list.map { it.toDomain() } }

    fun observeTotal(date: LocalDate): Flow<Long> = entries.observeTotalForDate(date.toString())

    suspend fun totalFor(date: LocalDate): Long = entries.totalForDate(date.toString())

    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Long>> =
        entries.observeDailyTotals(from.toString(), to.toString())
            .map { rows -> rows.associate { LocalDate.parse(it.date) to it.total } }

    suspend fun latestEntryAt(): Instant? = entries.latestTimestamp()?.let(Instant::ofEpochMilli)

    suspend fun getEntry(id: Long): WaterEntry? = entries.getById(id)?.toDomain()

    /** Adds an entry at an explicit wall-clock moment; returns the new id. */
    suspend fun addEntry(amountMl: Int, at: ZonedDateTime): Long {
        val nowMs = time.now().toEpochMilli()
        return entries.insert(
            WaterEntryEntity(
                amountMl = amountMl,
                timestamp = at.toInstant().toEpochMilli(),
                loggedLocalDate = at.toLocalDate().toString(),
                loggedLocalTime = at.toLocalTime().withNano(0).format(TIME),
                zoneId = at.zone.id,
                createdAt = nowMs,
                updatedAt = nowMs,
            ),
        )
    }

    /** Quick Add: current local date and time. */
    suspend fun addNow(amountMl: Int): Long = addEntry(amountMl, time.zonedNow())

    /** Explicit edit: amount and date attribution change together in one transaction. */
    suspend fun updateEntry(id: Long, amountMl: Int, at: ZonedDateTime): Boolean = tx.run {
        val existing = entries.getById(id) ?: return@run false
        entries.update(
            existing.copy(
                amountMl = amountMl,
                timestamp = at.toInstant().toEpochMilli(),
                loggedLocalDate = at.toLocalDate().toString(),
                loggedLocalTime = at.toLocalTime().withNano(0).format(TIME),
                zoneId = at.zone.id,
                updatedAt = time.now().toEpochMilli(),
            ),
        ) > 0
    }

    /** Deletes and returns the exact removed record so it can be restored once. */
    suspend fun deleteEntry(id: Long): WaterEntry? = tx.run {
        val existing = entries.getById(id) ?: return@run null
        entries.deleteById(id)
        existing.toDomain()
    }

    /** Restores an exact deleted entry (same id and fields). No-op if it already exists. */
    suspend fun restoreEntry(entry: WaterEntry): Boolean = entries.insertIfAbsent(entry.toEntity()) != -1L

    suspend fun removeAddedEntry(id: Long): Boolean = entries.deleteById(id) > 0

    // ---- goals ---------------------------------------------------------------------------

    fun observeGoalChanges(): Flow<List<GoalChange>> =
        goals.observeAll().map { list -> list.map { GoalChange(LocalDate.parse(it.effectiveLocalDate), it.goalMl) } }

    suspend fun goalChanges(): List<GoalChange> =
        goals.getAll().map { GoalChange(LocalDate.parse(it.effectiveLocalDate), it.goalMl) }

    suspend fun goalFor(date: LocalDate): Int? = GoalResolver.goalFor(date, goalChanges())

    /** Saves a goal effective from [effectiveDate]; earlier dates are never rewritten. */
    suspend fun setGoal(goalMl: Int, effectiveDate: LocalDate) = tx.run {
        goals.deleteAfter(effectiveDate.toString())
        goals.upsert(GoalChangeEntity(effectiveDate.toString(), goalMl, time.now().toEpochMilli()))
    }

    suspend fun clearAll() = tx.run {
        entries.deleteAll()
        goals.deleteAll()
    }

    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

internal fun WaterEntryEntity.toDomain() = WaterEntry(
    id = id,
    amountMl = amountMl,
    timestamp = Instant.ofEpochMilli(timestamp),
    localDate = LocalDate.parse(loggedLocalDate),
    localTime = LocalTime.parse(loggedLocalTime),
    zoneId = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault()),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun WaterEntry.toEntity() = WaterEntryEntity(
    id = id,
    amountMl = amountMl,
    timestamp = timestamp.toEpochMilli(),
    loggedLocalDate = localDate.toString(),
    loggedLocalTime = localTime.withNano(0).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
    zoneId = zoneId.id,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)
