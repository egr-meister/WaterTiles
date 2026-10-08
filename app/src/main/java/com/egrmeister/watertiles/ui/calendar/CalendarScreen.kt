package com.egrmeister.watertiles.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.egrmeister.watertiles.AppContainer
import com.egrmeister.watertiles.R
import com.egrmeister.watertiles.core.Format
import com.egrmeister.watertiles.domain.calendar.CalendarMath
import com.egrmeister.watertiles.domain.goals.GoalChange
import com.egrmeister.watertiles.domain.goals.GoalResolver
import com.egrmeister.watertiles.ui.theme.LocalTileColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

data class CalendarUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.MIN,
    val totals: Map<LocalDate, Long> = emptyMap(),
    val goals: List<GoalChange> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val c: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(c.time.today()))

    val state: StateFlow<CalendarUiState> = month.flatMapLatest { ym ->
        combine(
            c.repository.observeDailyTotals(ym.atDay(1), ym.atEndOfMonth()),
            c.repository.observeGoalChanges(),
            c.todayClock.today,
        ) { totals, goals, today ->
            CalendarUiState(loading = false, month = ym, today = today, totals = totals, goals = goals)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun previous() {
        month.value = month.value.minusMonths(1)
    }

    fun next() {
        month.value = month.value.plusMonths(1)
    }
}

@Composable
fun CalendarScreen(vm: CalendarViewModel, onOpenDay: (LocalDate) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    if (s.loading) return
    val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val cells = CalendarMath.monthCells(s.month, firstDay)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previous) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous month")
                }
                Text(
                    Format.monthYear(s.month.atDay(1)),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = vm::next) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month")
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { }) {
                CalendarMath.weekdayOrder(firstDay).forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.SHORT, Locale.US),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(0.85f).padding(1.dp)) {
                            if (date != null) {
                                DayCell(date, s, onOpenDay)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Legend()
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, s: CalendarUiState, onOpenDay: (LocalDate) -> Unit) {
    val total = s.totals[date] ?: 0L
    val goal = GoalResolver.goalFor(date, s.goals)
    val future = CalendarMath.isFuture(date, s.today)
    val isToday = date == s.today
    val reached = goal != null && total >= goal
    val tileColors = LocalTileColors.current
    val label = buildString {
        append(Format.longDate(date))
        if (isToday) append(", today")
        append(": ")
        when {
            future -> append("future date, read-only")
            total == 0L -> append("No records")
            else -> {
                append("${Format.ml(total)} recorded")
                if (goal != null) append(if (reached) ", goal reached" else ", goal ${Format.ml(goal)} not reached")
            }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val base = Modifier
        .fillMaxSize()
        .clip(shape)
        .then(if (isToday) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
        .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
    val interactive = if (!future) {
        Modifier.clickable(role = Role.Button, onClickLabel = "Open day") { onOpenDay(date) }
    } else {
        Modifier
    }
    Column(
        modifier = base.then(interactive).heightIn(min = 48.dp).clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (future) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
        )
        // Recorded-volume indicator: bar length relative to goal (or 2 L when no goal), capped.
        val ref = (goal ?: 2_000).toFloat()
        val frac = (total / ref).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tileColors.track),
        ) {
            if (total > 0) {
                Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.08f)).height(4.dp).background(tileColors.fill))
            }
        }
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            if (reached) {
                // Goal-reached marker: filled circle with a check-like dot ring, not color alone.
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(tileColors.outline),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface)
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Column {
        Text(
            "Bars show recorded volume. ✓ marks days that reached the goal saved for that date.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Days without records show “No records”. Future dates are read-only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
