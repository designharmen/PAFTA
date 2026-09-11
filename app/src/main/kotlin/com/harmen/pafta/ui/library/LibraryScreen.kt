package com.harmen.pafta.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmen.pafta.project.FileFormat
import com.harmen.pafta.project.ProjectEntry
import com.harmen.pafta.ui.chrome.HairlineDivider
import com.harmen.pafta.ui.chrome.Monogram
import com.harmen.pafta.ui.state.LibraryState
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.metrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The project library — PAFTA's file manager.
 *
 * The list is the directory on disk, nothing more: what is shown is what exists.
 * Projects whose file will not read are surfaced in their own section rather than
 * being hidden, because a project silently missing from the library is the worst
 * possible failure mode for a tool people keep drawings in.
 */
@Composable
public fun LibraryScreen(
    state: LibraryState,
    onImport: () -> Unit,
    onOpen: (ProjectEntry) -> Unit,
    onDelete: (ProjectEntry) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(HarmenColours.Ground)) {
        LibraryBar(onImport = onImport, importing = state.importing)
        HairlineDivider()

        state.error?.let { ErrorBanner(it, onDismissError) }

        when {
            state.loading -> CentredNote { Spinner() }

            state.projects.isEmpty() && state.unreadable.isEmpty() -> EmptyLibrary(onImport)

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.projects, key = { it.file.path }) { entry ->
                    ProjectRow(
                        entry = entry,
                        onOpen = { onOpen(entry) },
                        onDelete = { onDelete(entry) },
                    )
                }

                if (state.unreadable.isNotEmpty()) {
                    item {
                        Text(
                            text = "[Unreadable]",
                            style = HarmenType.SectionTitle,
                            color = HarmenColours.TextMuted,
                            modifier = Modifier.padding(
                                start = metrics.gutter,
                                end = metrics.gutter,
                                top = metrics.gutter,
                                bottom = 6.dp,
                            ),
                        )
                    }
                    items(state.unreadable, key = { it.path }) { file ->
                        UnreadableRow(file.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryBar(onImport: () -> Unit, importing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmenColours.Panel)
            .height(metrics.topBarRowHeight)
            .padding(horizontal = metrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(letter = "P")
        Spacer(Modifier.width(metrics.gutter))
        Text(
            text = "PROJECTS",
            style = HarmenType.MenuCaps,
            color = HarmenColours.TextMuted,
        )
        Spacer(Modifier.weight(1f))
        if (importing) {
            Spinner(size = 14.dp)
            Spacer(Modifier.width(metrics.gutterTight))
        }
        OutlinedAction(text = "IMPORT", enabled = !importing, onClick = onImport)
    }
}

@Composable
private fun OutlinedAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colour = if (enabled) HarmenColours.Text else HarmenColours.TextFaint
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .drawBehind {
                drawRect(
                    color = if (enabled) HarmenColours.Accent else HarmenColours.Hairline,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = text, style = HarmenType.MenuCaps, color = colour)
    }
}

/** One project: format chip, name, then source file, size and date. */
@Composable
private fun ProjectRow(entry: ProjectEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = metrics.gutter, vertical = 11.dp),
    ) {
        FormatChip(entry.format)
        Spacer(Modifier.width(metrics.gutter))

        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = HarmenType.Body,
                color = HarmenColours.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(entry.manifest.source.fileName)
                    append("  ·  ")
                    append(formatSize(entry.sizeBytes))
                    append("  ·  ")
                    append(formatDate(entry.modifiedAtEpochMs))
                },
                style = HarmenType.Status,
                color = HarmenColours.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(metrics.gutterTight))
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "Delete ${entry.name}",
            tint = HarmenColours.TextFaint,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(metrics.cornerRadius))
                .clickable(role = Role.Button, onClick = onDelete)
                .padding(7.dp),
        )
    }
    HairlineDivider()
}

/**
 * The format badge. A format PAFTA cannot draw yet is shown in the faint tone
 * rather than hidden, so the user can see their file is safely stored and simply
 * not viewable in this build.
 */
@Composable
private fun FormatChip(format: FileFormat?) {
    val text = format?.extension?.uppercase(Locale.ROOT) ?: "?"
    val viewable = format?.readable == true
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(HarmenColours.PanelRaised)
            .drawBehind {
                drawRect(
                    color = if (viewable) HarmenColours.Accent else HarmenColours.Hairline,
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
    ) {
        Text(
            text = text,
            style = HarmenType.Status,
            color = if (viewable) HarmenColours.Accent else HarmenColours.TextFaint,
            maxLines = 1,
        )
    }
}

@Composable
private fun UnreadableRow(fileName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.gutter, vertical = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = HarmenColours.TextFaint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(metrics.gutter))
        Column(Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = HarmenType.Body,
                color = HarmenColours.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "this file is not a readable PAFTA project",
                style = HarmenType.Status,
                color = HarmenColours.TextFaint,
            )
        }
    }
    HairlineDivider()
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    CentredNote {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = HarmenColours.TextFaint,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(metrics.gutter))
            Text(
                text = "no projects yet",
                style = HarmenType.Body,
                color = HarmenColours.TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "import a DXF drawing to begin",
                style = HarmenType.PropertyKey,
                color = HarmenColours.TextFaint,
            )
            Spacer(Modifier.height(metrics.gutter))
            OutlinedAction(text = "IMPORT", enabled = true, onClick = onImport)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmenColours.AccentWash)
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(horizontal = metrics.gutter, vertical = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = HarmenColours.Accent,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(metrics.gutterTight))
        Text(
            text = message,
            style = HarmenType.Body,
            color = HarmenColours.Text,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(metrics.gutterTight))
        Text(text = "DISMISS", style = HarmenType.MenuCaps, color = HarmenColours.Accent)
    }
    HairlineDivider()
}

@Composable
private fun CentredNote(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Spinner(size: androidx.compose.ui.unit.Dp = 22.dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = HarmenColours.Accent,
        strokeWidth = 1.5.dp,
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.ROOT, "%.1fMB", bytes / 1_048_576.0)
    bytes >= 1_024 -> "${bytes / 1_024}KB"
    else -> "${bytes}B"
}

private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0) {
        "—"
    } else {
        SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
