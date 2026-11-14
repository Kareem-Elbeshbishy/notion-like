package app.workspace.ui.database

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.workspace.data.Option
import app.workspace.data.Prop
import app.workspace.data.PropType
import app.workspace.data.ViewConfig
import app.workspace.ui.theme.AppTheme
import app.workspace.ui.theme.Dim
import app.workspace.ui.theme.Radius
import app.workspace.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DayTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
private fun Hint(text: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.textMuted, modifier = Modifier.padding(Dim.pagePadding))
}

@Composable
private fun NewRowButton(label: String = "+ New row", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(label, style = AppTheme.type.small, color = AppTheme.colors.textMuted) }
}

/** The three-dot menu every card and list line has: open, or move to the trash. */
@Composable
private fun RowMenu(onOpen: () -> Unit, onTrash: () -> Unit, extra: @Composable (close: () -> Unit) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(Dim.touchMin).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MoreVert, contentDescription = "Row options", tint = AppTheme.colors.textMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Open") }, onClick = { open = false; onOpen() })
            extra { open = false }
            DropdownMenuItem(text = { Text("Move to trash") }, onClick = { open = false; onTrash() })
        }
    }
}

@Composable
private fun RowCard(
    row: RowUi,
    props: List<Prop>,
    onOpen: () -> Unit,
    onTrash: () -> Unit,
    extra: @Composable (close: () -> Unit) -> Unit = {},
) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface)
            .clickable(onClick = onOpen).padding(start = Space.s12, top = Space.s4, bottom = Space.s12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (row.title.isBlank()) "Untitled" else row.title,
                style = AppTheme.type.body,
                color = if (row.title.isBlank()) c.textMuted else c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RowMenu(onOpen, onTrash, extra)
        }
        props.forEach { prop ->
            Box(Modifier.padding(top = Space.s4, end = Space.s12)) { CellView(prop, row.values) }
        }
    }
}

// ---- board -----------------------------------------------------------------------------

@Composable
fun BoardView(
    data: DbUi,
    view: ViewConfig,
    rows: List<RowUi>,
    vm: DatabaseViewModel,
    onOpenRow: (String) -> Unit,
    onRowTrashed: (String) -> Unit,
) {
    val group = data.props.firstOrNull {
        it.id == view.groupBy && (it.type == PropType.SELECT || it.type == PropType.STATUS)
    }
    if (group == null) {
        Hint("Pick a Select or Status property to group by: tap the view name, then choose Group by.")
        return
    }
    val known = group.options.map { it.id }.toSet()
    val columns = buildList<Pair<Option?, List<RowUi>>> {
        group.options.forEach { o -> add(o to rows.filter { it.values.optString(group.id) == o.id }) }
        add(null to rows.filter { it.values.optString(group.id) !in known })
    }
    LazyRow(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.s16),
        horizontalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        items(columns, key = { it.first?.id ?: "no-value" }) { (option, cards) ->
            BoardColumn(option, cards, group, data, vm, onOpenRow, onRowTrashed)
        }
    }
}

@Composable
private fun BoardColumn(
    option: Option?,
    cards: List<RowUi>,
    group: Prop,
    data: DbUi,
    vm: DatabaseViewModel,
    onOpenRow: (String) -> Unit,
    onRowTrashed: (String) -> Unit,
) {
    val c = AppTheme.colors
    val shown = data.props.filter { it.id != group.id }.take(3)
    Column(Modifier.width(Dim.boardColumn).fillMaxHeight().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(bottom = Space.s8), verticalAlignment = Alignment.CenterVertically) {
            if (option != null) TagChip(option.name, option.color) else Text("No value", style = AppTheme.type.small, color = c.textMuted)
            Text("  " + cards.size, style = AppTheme.type.small, color = c.textMuted)
        }
        cards.forEach { row ->
            RowCard(
                row = row,
                props = shown,
                onOpen = { onOpenRow(row.pageId) },
                onTrash = {
                    vm.trashRow(row.pageId)
                    onRowTrashed(row.pageId)
                },
                extra = { close ->
                    group.options.filter { it.id != option?.id }.forEach { target ->
                        DropdownMenuItem(
                            text = { Text("Move to " + target.name) },
                            onClick = {
                                close()
                                vm.setCell(row.pageId, group, target.id)
                            },
                        )
                    }
                    if (option != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from group") },
                            onClick = {
                                close()
                                vm.setCell(row.pageId, group, null)
                            },
                        )
                    }
                },
            )
            Spacer(Modifier.height(Space.s8))
        }
        NewRowButton("+ New") {
            vm.addRow(initial = if (option != null) mapOf(group.id to option.id) else emptyMap(), onCreated = onOpenRow)
        }
    }
}

// ---- list ------------------------------------------------------------------------------

@Composable
fun ListView(
    data: DbUi,
    rows: List<RowUi>,
    vm: DatabaseViewModel,
    onOpenRow: (String) -> Unit,
    onRowTrashed: (String) -> Unit,
) {
    val c = AppTheme.colors
    val shown = data.props.take(2)
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.pageId }) { row ->
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable { onOpenRow(row.pageId) }
                        .padding(start = Dim.pagePadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (row.title.isBlank()) "Untitled" else row.title,
                        style = AppTheme.type.body,
                        color = if (row.title.isBlank()) c.textMuted else c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    shown.forEach { prop ->
                        Box(Modifier.padding(start = Space.s8)) { CellView(prop, row.values) }
                    }
                    RowMenu(
                        onOpen = { onOpenRow(row.pageId) },
                        onTrash = {
                            vm.trashRow(row.pageId)
                            onRowTrashed(row.pageId)
                        },
                    )
                }
                HorizontalDivider(color = c.border)
            }
        }
        item(key = "new-row") { NewRowButton { vm.addRow(onCreated = onOpenRow) } }
    }
}

// ---- gallery ---------------------------------------------------------------------------

@Composable
fun GalleryView(
    data: DbUi,
    rows: List<RowUi>,
    vm: DatabaseViewModel,
    onOpenRow: (String) -> Unit,
    onRowTrashed: (String) -> Unit,
) {
    val shown = data.props.take(3)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.s16),
        horizontalArrangement = Arrangement.spacedBy(Space.s12),
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        items(rows, key = { it.pageId }) { row ->
            RowCard(
                row = row,
                props = shown,
                onOpen = { onOpenRow(row.pageId) },
                onTrash = {
                    vm.trashRow(row.pageId)
                    onRowTrashed(row.pageId)
                },
            )
        }
        item(key = "new-row", span = { GridItemSpan(maxLineSpan) }) {
            NewRowButton { vm.addRow(onCreated = onOpenRow) }
        }
    }
}

// ---- calendar --------------------------------------------------------------------------

@Composable
fun CalendarView(
    data: DbUi,
    view: ViewConfig,
    rows: List<RowUi>,
    vm: DatabaseViewModel,
    onOpenRow: (String) -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val dateProp = data.props.firstOrNull { it.id == view.dateProp && it.type == PropType.DATE }
    if (dateProp == null) {
        Hint("Pick a Date property for this calendar: tap the view name, then choose Date property.")
        return
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val byDate = remember(rows, dateProp) {
        rows.mapNotNull { row ->
            runCatching { LocalDate.parse(row.values.optString(dateProp.id)) }.getOrNull()?.let { it to row }
        }.groupBy({ it.first }, { it.second })
    }
    val today = LocalDate.now()
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    val cellHeight = Dim.touchMin + Space.s16

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("\u2039") }
            Text(
                month.month.getDisplayName(DayTextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = t.h3, color = c.text, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                month = YearMonth.now()
                selected = today
            }) { Text("Today") }
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("\u203A") }
        }
        Row(Modifier.fillMaxWidth()) {
            for (k in 0 until 7) {
                val day = DayOfWeek.of((firstDay.value - 1 + k) % 7 + 1)
                Text(
                    day.getDisplayName(DayTextStyle.SHORT, Locale.getDefault()),
                    style = t.caption, color = c.textMuted, maxLines = 1, modifier = Modifier.weight(1f).padding(Space.s4),
                )
            }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (k in 0 until 7) {
                    val dayNumber = w * 7 + k - lead + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(cellHeight))
                    } else {
                        val date = month.atDay(dayNumber)
                        val count = byDate[date]?.size ?: 0
                        Column(
                            Modifier.weight(1f).height(cellHeight).padding(Space.s2).clip(RoundedCornerShape(Radius.sm))
                                .background(if (date == selected) c.accentSoft else c.bg)
                                .then(if (date == today) Modifier.border(Space.s2 / 2, c.accent, RoundedCornerShape(Radius.sm)) else Modifier)
                                .clickable { selected = date }.padding(Space.s4),
                        ) {
                            Text(dayNumber.toString(), style = t.small, color = c.text)
                            if (count > 0) Text("\u25CF".repeat(minOf(count, 3)), style = t.caption, color = c.accent, maxLines = 1)
                        }
                    }
                }
            }
        }
        val day = selected
        if (day != null) {
            HorizontalDivider(color = c.border, modifier = Modifier.padding(top = Space.s8))
            Text(day.toString(), style = t.h3, color = c.text, modifier = Modifier.padding(vertical = Space.s8, horizontal = Space.s8))
            (byDate[day] ?: emptyList()).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dim.touchMin).clickable { onOpenRow(row.pageId) }.padding(horizontal = Space.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (row.title.isBlank()) "Untitled" else row.title,
                        style = t.body, color = if (row.title.isBlank()) c.textMuted else c.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            NewRowButton("+ New row on this day") {
                vm.addRow(initial = mapOf(dateProp.id to day.toString()), onCreated = onOpenRow)
            }
        } else {
            Hint("Tap a day to see its rows.")
        }
    }
}
