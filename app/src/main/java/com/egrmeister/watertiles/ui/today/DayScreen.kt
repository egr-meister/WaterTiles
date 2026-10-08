package com.egrmeister.watertiles.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.watertiles.R
import com.egrmeister.watertiles.core.Format
import com.egrmeister.watertiles.domain.entries.WaterEntry
import com.egrmeister.watertiles.domain.mosaic.MosaicText
import com.egrmeister.watertiles.domain.mosaic.Tile
import com.egrmeister.watertiles.ui.components.mosaicColumns
import com.egrmeister.watertiles.ui.components.mosaicRows
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    vm: DayViewModel,
    showBack: Boolean,
    onBack: () -> Unit,
    onAddEntry: (LocalDate) -> Unit,
    onEditEntry: (LocalDate, Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedTile by remember { mutableStateOf<Tile?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.refreshToday()
        onPauseOrDispose { }
    }

    LaunchedEffect(vm) {
        vm.events.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            val undo = msg.undo
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = if (undo != null) "Undo" else null,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (undo != null) {
                if (result == SnackbarResult.ActionPerformed) vm.undo(undo) else vm.undoExpired(undo)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (showBack) {
                TopAppBar(
                    title = { Text(if (state.loading) "" else Format.shortDate(state.date)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to calendar")
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) return@Scaffold
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 720.dp
            val mosaicWidth = if (wide) maxWidth * 0.55f else maxWidth
            val columns = mosaicColumns(mosaicWidth - 32.dp)
            val tileSize = (mosaicWidth - 32.dp) / columns
            val onTile: (Tile) -> Unit = { selectedTile = it }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.width(mosaicWidth).fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        dayHeader(state)
                        state.mosaic?.let { mosaicRows(it, columns, tileSize, onTile) }
                        loggingControls(state, vm, onAddEntry)
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        entryList(state, onEditEntry, vm::delete)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    dayHeader(state)
                    state.mosaic?.let { mosaicRows(it, columns, tileSize, onTile) }
                    loggingControls(state, vm, onAddEntry)
                    entryList(state, onEditEntry, vm::delete)
                }
            }
        }
    }

    selectedTile?.let { tile ->
        ModalBottomSheet(onDismissRequest = { selectedTile = null }) {
            TileDetail(tile)
        }
    }
}

private fun LazyListScope.dayHeader(state: DayUiState) {
    item(key = "header") {
        val mosaic = state.mosaic
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                text = if (state.isToday) "Today · ${Format.shortDate(state.date)}" else Format.longDate(state.date),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(4.dp))
            if (mosaic != null && state.goalMl != null) {
                Text(
                    text = "${Format.ml(mosaic.recordedMl)} of ${Format.ml(state.goalMl)}",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "${mosaic.percent}% of your selected goal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (mosaic.overflowMl > 0) {
                    Text(
                        text = MosaicText.overflowText(mosaic.overflowMl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (mosaic != null) {
                Text("Recorded volume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.ml(mosaic.recordedMl), style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Set a goal to see goal progress.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LazyListScope.loggingControls(
    state: DayUiState,
    vm: DayViewModel,
    onAddEntry: (LocalDate) -> Unit,
) {
    item(key = "controls") {
        val mosaic = state.mosaic
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                MosaicText.tileSizeCaption(state.settings.tileSizeMl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mosaic != null) {
                Text(
                    MosaicText.summary(mosaic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!state.isFuture) {
                Button(
                    onClick = { onAddEntry(state.date) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
                ) {
                    Text("Add Water", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (state.isToday) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.settings.quickPortions.forEach { amount ->
                        OutlinedButton(
                            onClick = { vm.quickAdd(amount, System.nanoTime()) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "+${Format.number(amount.toLong())}",
                                maxLines = 1,
                                modifier = Modifier.semantics { contentDescription = "Quick add ${Format.ml(amount)}" },
                            )
                        }
                    }
                }
                Text(
                    "Quick portions are added at the current time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun LazyListScope.entryList(
    state: DayUiState,
    onEdit: (LocalDate, Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    item(key = "entries-title") {
        Text(
            "Entries",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
        )
    }
    if (state.entries.isEmpty()) {
        item(key = "entries-empty") {
            Text(
                if (state.isToday) "Add your first water entry." else "No records for this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(state.entries, key = { "entry-${it.id}" }) { entry ->
            EntryRow(entry, onEdit = { onEdit(entry.localDate, entry.id) }, onDelete = { onDelete(entry.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun EntryRow(entry: WaterEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = "${Format.ml(entry.amountMl)} at ${Format.time(entry.localTime)}"
            },
        ) {
            Text(Format.ml(entry.amountMl), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                Format.time(entry.localTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit ${Format.ml(entry.amountMl)} entry")
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete ${Format.ml(entry.amountMl)} entry")
        }
    }
}

@Composable
private fun TileDetail(tile: Tile) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Tile ${tile.number}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.size(12.dp))
            DetailLine("Capacity", Format.ml(tile.capacityMl) + if (tile.isReducedCapacity) " (final tile, reduced)" else "")
            DetailLine("Filled", Format.ml(tile.filledMl))
            DetailLine("State", MosaicText.tileStateLabel(tile))
            Spacer(Modifier.size(12.dp))
            Text(
                "Tiles illustrate volume. To change amounts, edit your entries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
