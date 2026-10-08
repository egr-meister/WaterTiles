package com.egrmeister.watertiles.domain.goals

import java.time.LocalDate

/** An effective-dated goal record. */
data class GoalChange(val effectiveDate: LocalDate, val goalMl: Int)

enum class GoalApply { TODAY, TOMORROW }

object GoalResolver {
    /** Goal effective on [date]: the latest change dated on or before it, or null. */
    fun goalFor(date: LocalDate, changes: List<GoalChange>): Int? =
        changes.asSequence()
            .filter { !it.effectiveDate.isAfter(date) }
            .maxByOrNull { it.effectiveDate }
            ?.goalMl

    fun effectiveDateFor(apply: GoalApply, today: LocalDate): LocalDate = when (apply) {
        GoalApply.TODAY -> today
        GoalApply.TOMORROW -> today.plusDays(1)
    }

    /**
     * Result of saving a new goal: records dated before the effective date are kept untouched;
     * the record on the effective date is replaced and any later pending change is superseded.
     */
    fun apply(changes: List<GoalChange>, newGoal: GoalChange): List<GoalChange> =
        (changes.filter { it.effectiveDate.isBefore(newGoal.effectiveDate) } + newGoal)
            .sortedBy { it.effectiveDate }

    /** Next goal scheduled after [today], if any (e.g. "applies tomorrow"). */
    fun pendingAfter(today: LocalDate, changes: List<GoalChange>): GoalChange? =
        changes.filter { it.effectiveDate.isAfter(today) }.minByOrNull { it.effectiveDate }
}
