package com.egrmeister.watertiles.data

import com.egrmeister.watertiles.core.FixedTimeProvider
import com.egrmeister.watertiles.data.local.DailyTotalRow
import com.egrmeister.watertiles.data.local.GoalChangeDao
import com.egrmeister.watertiles.data.local.GoalChangeEntity
import com.egrmeister.watertiles.data.local.TransactionRunner
import com.egrmeister.watertiles.data.local.WaterEntryDao
import com.egrmeister.watertiles.data.local.WaterEntryEntity
import com.egrmeister.watertiles.data.repository.WaterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WaterRepositoryTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val time = FixedTimeProvider(ZonedDateTime.of(2026, 5, 4, 23, 30, 0, 0, berlin).toInstant(), berlin)
    private val entries = FakeEntryDao()
    private val goals = FakeGoalDao()
    private val repo = WaterRepository(entries, goals, object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }, time)
    private val may4 = LocalDate.of(2026, 5, 4)
    private val may3 = LocalDate.of(2026, 5, 3)

    @Test fun quickAddUsesCurrentLocalDateAndExactTotals() = runTest {
        repo.addNow(250)
        repo.addNow(333)
        assertEquals(583L, repo.totalFor(may4))
        val list = repo.observeEntries(may4).first()
        assertEquals(2, list.size)
        assertEquals(berlin, list[0].zoneId)
    }

    @Test fun editingMovesEntryBetweenDates() = runTest {
        val id = repo.addNow(400)
        repo.updateEntry(id, 450, ZonedDateTime.of(2026, 5, 3, 20, 0, 0, 0, berlin))
        assertEquals(0L, repo.totalFor(may4))
        assertEquals(450L, repo.totalFor(may3))
        assertEquals("20:00:00", entries.rows[id]!!.loggedLocalTime)
    }

    @Test fun deleteAndUndoRestoresExactEntryOnce() = runTest {
        val id = repo.addNow(300)
        val before = entries.rows[id]!!
        val removed = repo.deleteEntry(id)
        assertNotNull(removed)
        assertEquals(0L, repo.totalFor(may4))
        assertTrue(repo.restoreEntry(removed!!))
        assertEquals(before, entries.rows[id])
        assertFalse(repo.restoreEntry(removed)) // second restore is a no-op
        assertEquals(300L, repo.totalFor(may4))
    }

    @Test fun undoQuickAddRemovesOnlyThatEntry() = runTest {
        repo.addNow(100)
        val id = repo.addNow(200)
        assertTrue(repo.removeAddedEntry(id))
        assertFalse(repo.removeAddedEntry(id))
        assertEquals(100L, repo.totalFor(may4))
    }

    @Test fun travelDoesNotMoveStoredEntries() = runTest {
        repo.addNow(500) // 23:30 on May 4 in Berlin
        time.zoneId = ZoneId.of("America/New_York") // device now reports 17:30 May 4 in New York
        time.instant = time.instant.plusSeconds(8 * 3600) // and later it's May 5 in Berlin
        assertEquals(500L, repo.totalFor(may4))
        assertEquals(0L, repo.totalFor(may4.plusDays(1)))
    }

    @Test fun goalChangesAreEffectiveDated() = runTest {
        repo.setGoal(1500, may3)
        repo.setGoal(2000, may4.plusDays(1))
        assertEquals(1500, repo.goalFor(may4))
        assertEquals(2000, repo.goalFor(may4.plusDays(1)))
        repo.setGoal(1800, may4)
        assertEquals(1500, repo.goalFor(may3))
        assertEquals(1800, repo.goalFor(may4.plusDays(1)))
        assertNull(repo.goalFor(may3.minusDays(1)))
    }

    @Test fun clearAllRemovesData() = runTest {
        repo.addNow(100)
        repo.setGoal(1500, may4)
        repo.clearAll()
        assertEquals(0L, repo.totalFor(may4))
        assertNull(repo.goalFor(may4))
    }
}

private class FakeEntryDao : WaterEntryDao {
    val rows = linkedMapOf<Long, WaterEntryEntity>()
    private val flow = MutableStateFlow(0)
    private var nextId = 1L
    private fun changed() { flow.value++ }

    override fun observeForDate(date: String): Flow<List<WaterEntryEntity>> = flow.map {
        rows.values.filter { it.loggedLocalDate == date }
            .sortedWith(compareByDescending<WaterEntryEntity> { it.loggedLocalTime }.thenByDescending { it.timestamp })
    }
    override fun observeTotalForDate(date: String): Flow<Long> = flow.map { sum(date) }
    override suspend fun totalForDate(date: String): Long = sum(date)
    private fun sum(date: String) = rows.values.filter { it.loggedLocalDate == date }.sumOf { it.amountMl.toLong() }
    override fun observeDailyTotals(from: String, to: String): Flow<List<DailyTotalRow>> = flow.map {
        rows.values.filter { it.loggedLocalDate in from..to }.groupBy { it.loggedLocalDate }
            .map { (d, l) -> DailyTotalRow(d, l.sumOf { it.amountMl.toLong() }) }
    }
    override suspend fun latestTimestamp(): Long? = rows.values.maxOfOrNull { it.timestamp }
    override suspend fun getById(id: Long): WaterEntryEntity? = rows[id]
    override suspend fun insert(entity: WaterEntryEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        check(id !in rows)
        rows[id] = entity.copy(id = id); changed(); return id
    }
    override suspend fun insertIfAbsent(entity: WaterEntryEntity): Long {
        if (entity.id in rows) return -1
        rows[entity.id] = entity; changed(); return entity.id
    }
    override suspend fun update(entity: WaterEntryEntity): Int {
        if (entity.id !in rows) return 0
        rows[entity.id] = entity; changed(); return 1
    }
    override suspend fun delete(entity: WaterEntryEntity): Int = deleteById(entity.id)
    override suspend fun deleteById(id: Long): Int = if (rows.remove(id) != null) { changed(); 1 } else 0
    override suspend fun deleteAll() { rows.clear(); changed() }
}

private class FakeGoalDao : GoalChangeDao {
    val rows = sortedMapOf<String, GoalChangeEntity>()
    private val flow = MutableStateFlow(0)
    override fun observeAll(): Flow<List<GoalChangeEntity>> = flow.map { rows.values.toList() }
    override suspend fun getAll(): List<GoalChangeEntity> = rows.values.toList()
    override suspend fun upsert(entity: GoalChangeEntity) { rows[entity.effectiveLocalDate] = entity; flow.value++ }
    override suspend fun deleteAfter(date: String) { rows.keys.filter { it > date }.forEach { rows.remove(it) }; flow.value++ }
    override suspend fun deleteAll() { rows.clear(); flow.value++ }
}
