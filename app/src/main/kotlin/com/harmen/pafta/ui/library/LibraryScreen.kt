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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.harmen.pafta.BuildConfig
import com.harmen.pafta.R
import com.harmen.pafta.project.FileFormat
import com.harmen.pafta.project.ProjectEntry
import com.harmen.pafta.ui.state.UpdateState
import com.harmen.pafta.ui.chrome.HairlineDivider
import com.harmen.pafta.ui.chrome.Monogram
import com.harmen.pafta.ui.mesaj
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
    updateState: UpdateState = UpdateState.Idle,
    onUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    /** Asks for a name for a project started here rather than imported. */
    onBeginNewProject: () -> Unit = {},
    onCancelNewProject: () -> Unit = {},
    onCreateProject: (String) -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(HarmenColours.Ground)) {
        LibraryBar(
            onImport = onImport,
            onNewProject = onBeginNewProject,
            importing = state.importing,
            updateState = updateState,
            onUpdate = onUpdate,
        )
        HairlineDivider()

        // The name is asked for in place, at the top of the list, rather than in
        // a box over it: on a tablet a dialog puts the keyboard over the thing
        // it is asking about, and there is nothing behind this one worth seeing.
        if (state.naming) {
            NewProjectSheet(onCreate = onCreateProject, onCancel = onCancelNewProject)
        }

        if (updateState !is UpdateState.Idle) {
            UpdateBanner(updateState, onDismissUpdate)
        }

        state.error?.let { ErrorBanner(it.mesaj(), onDismissError) }

        when {
            state.loading -> CentredNote { Spinner() }

            state.projects.isEmpty() && state.unreadable.isEmpty() ->
                EmptyLibrary(onImport = onImport, onNewProject = onBeginNewProject)

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
                            text = "[${stringResource(R.string.library_unreadable)}]",
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
private fun LibraryBar(
    onImport: () -> Unit,
    onNewProject: () -> Unit,
    importing: Boolean,
    updateState: UpdateState,
    onUpdate: () -> Unit,
) {
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
            text = stringResource(R.string.library_title),
            style = HarmenType.MenuCaps,
            color = HarmenColours.TextMuted,
        )
        Spacer(Modifier.weight(1f))
        // Which build is actually on the device. Quiet enough to ignore, and
        // the one thing that makes a report from the device unambiguous.
        Text(
            text = stringResource(R.string.library_build, BuildConfig.BUILD_LABEL),
            style = HarmenType.Status,
            color = HarmenColours.TextFaint,
        )
        Spacer(Modifier.width(metrics.gutter))
        // One button for the whole update: look, fetch, hand to the installer.
        val busy = updateState is UpdateState.Checking || updateState is UpdateState.Downloading
        OutlinedAction(text = R.string.library_update, enabled = !busy, onClick = onUpdate)
        Spacer(Modifier.width(metrics.gutterTight))
        if (importing) {
            Spinner(size = 14.dp)
            Spacer(Modifier.width(metrics.gutterTight))
        }
        OutlinedAction(text = R.string.library_import, enabled = !importing, onClick = onImport)
        Spacer(Modifier.width(metrics.gutterTight))
        // Starting a drawing here is the first thing most people want, so it is
        // the last thing on the row — nearest the thumb on a held tablet.
        OutlinedAction(text = R.string.library_new, enabled = !importing, onClick = onNewProject)
    }
}

@Composable
private fun OutlinedAction(@StringRes text: Int, enabled: Boolean, onClick: () -> Unit) {
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
        Text(text = stringResource(text), style = HarmenType.MenuCaps, color = colour)
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
                text = stringResource(
                    R.string.library_row_detail,
                    entry.manifest.source.fileName,
                    formatSize(entry.sizeBytes),
                    formatDate(entry.modifiedAtEpochMs),
                ),
                style = HarmenType.Status,
                color = HarmenColours.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(metrics.gutterTight))
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.library_delete, entry.name),
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
            // The chip's outline says in bronze whether PAFTA can draw this
            // format; the three letters inside stay ivory, since bronze text
            // this small is forbidden by the guideline.
            color = if (viewable) HarmenColours.Text else HarmenColours.TextFaint,
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
                text = stringResource(R.string.library_unreadable_hint),
                style = HarmenType.Status,
                color = HarmenColours.TextFaint,
            )
        }
    }
    HairlineDivider()
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, onNewProject: () -> Unit) {
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
                text = stringResource(R.string.library_empty_title),
                style = HarmenType.Body,
                color = HarmenColours.TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.library_empty_hint),
                style = HarmenType.PropertyKey,
                color = HarmenColours.TextFaint,
            )
            Spacer(Modifier.height(metrics.gutter))
            Row {
                OutlinedAction(
                    text = R.string.library_new,
                    enabled = true,
                    onClick = onNewProject,
                )
                Spacer(Modifier.width(metrics.gutterTight))
                OutlinedAction(text = R.string.library_import, enabled = true, onClick = onImport)
            }
        }
    }
}

/**
 * Asks what the new project is called.
 *
 * One field and two buttons. The name is filled in already, so somebody who
 * does not want to think about it can tap OLUŞTUR and be drawing a second
 * later; somebody who does can select it and type over it.
 */
@Composable
private fun NewProjectSheet(onCreate: (String) -> Unit, onCancel: () -> Unit) {
    val suggested = stringResource(R.string.new_project_default)
    var name by remember(suggested) { mutableStateOf(suggested) }
    val create = { if (name.isNotBlank()) onCreate(name.trim()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmenColours.PanelRaised)
            .padding(horizontal = metrics.gutter, vertical = metrics.gutter),
    ) {
        Text(
            text = stringResource(R.string.new_project_title),
            style = HarmenType.SectionTitle,
            color = HarmenColours.Text,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.new_project_hint),
            style = HarmenType.Status,
            color = HarmenColours.TextFaint,
        )
        Spacer(Modifier.height(metrics.gutter))

        Text(
            text = stringResource(R.string.new_project_name),
            style = HarmenType.PropertyKey,
            color = HarmenColours.TextMuted,
        )
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = HarmenType.Body.copy(color = HarmenColours.Text),
            cursorBrush = SolidColor(HarmenColours.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { create() }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(metrics.cornerRadius))
                .background(HarmenColours.Panel)
                .drawBehind {
                    drawRect(color = HarmenColours.Hairline, style = Stroke(width = 1.dp.toPx()))
                }
                .padding(horizontal = 10.dp, vertical = 12.dp),
        )

        Spacer(Modifier.height(metrics.gutter))
        Row {
            OutlinedAction(
                text = R.string.new_project_create,
                enabled = name.isNotBlank(),
                onClick = create,
            )
            Spacer(Modifier.width(metrics.gutterTight))
            OutlinedAction(
                text = R.string.new_project_cancel,
                enabled = true,
                onClick = onCancel,
            )
        }
    }
    HairlineDivider()
}

/**
 * What the update button is doing, in one line.
 *
 * The sequence behind it has four steps; the person reading this has one
 * question — is there a new version and is it coming — so the line answers that
 * and nothing else.
 */
@Composable
private fun UpdateBanner(state: UpdateState, onDismiss: () -> Unit) {
    val message = when (state) {
        UpdateState.Idle -> return
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateState.Downloading -> stringResource(
            R.string.update_downloading,
            state.build,
            (state.progress.coerceAtLeast(0f) * 100).toInt(),
        )
        is UpdateState.Ready -> stringResource(R.string.update_ready, state.build)
        UpdateState.NeedsPermission -> stringResource(R.string.update_needs_permission)
        is UpdateState.Failed -> state.cause.mesaj()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmenColours.PanelRaised)
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(horizontal = metrics.gutter, vertical = 10.dp),
    ) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) {
            Spinner(size = 13.dp)
            Spacer(Modifier.width(metrics.gutterTight))
        }
        Text(
            text = message,
            style = HarmenType.Body,
            color = HarmenColours.Text,
            modifier = Modifier.weight(1f),
        )
    }
    HairlineDivider()
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
        Text(
            text = stringResource(R.string.library_dismiss),
            style = HarmenType.MenuCaps,
            color = HarmenColours.Text,
        )
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

/** Turkish locale, fixed: the interface is Turkish whatever the device is set to. */
private val TR: Locale = Locale.forLanguageTag("tr-TR")

@Composable
@ReadOnlyComposable
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> stringResource(
        R.string.size_megabytes,
        String.format(TR, "%.1f", bytes / 1_048_576.0),
    )

    bytes >= 1_024 -> stringResource(R.string.size_kilobytes, (bytes / 1_024).toInt())
    else -> stringResource(R.string.size_bytes, bytes.toInt())
}

@Composable
@ReadOnlyComposable
private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0) {
        stringResource(R.string.library_no_date)
    } else {
        SimpleDateFormat("d MMMM yyyy HH:mm", TR).format(Date(epochMs))
    }
