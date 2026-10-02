package com.valpr.bikecompanion.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.workout.WorkoutTextEvent
import com.valpr.bikecompanion.workout.WorkoutValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WorkoutEditorScreen(
    state: WorkoutEditorState,
    onSaved: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val issues = state.issues()
    val errors = issues.filter { it.isError }
    val warnings = issues.filter { !it.isError }
    val draft = state.buildWorkout()
    val footerErrors = errors.take(3).map { err ->
        val where = err.segmentIndex?.let { "Step ${it + 1}: " } ?: ""
        where + err.message
    }

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showOverwriteDialog by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val detailScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val viewRequesters = remember { mutableMapOf<Long, BringIntoViewRequester>() }
    // Handlebar mounts are often landscape: header + save rail on the left,
    // step list on the right (AGENTS.md §2 parity).
    val isLandscape =
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = state.isDirty) { showDiscardDialog = true }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this workout will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        state.discardChanges()
                        onNavigateBack()
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to original?") },
            text = { Text("Restores the bundled version of this workout. Your edits will be replaced.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        if (!state.resetToOriginal()) {
                            scope.launch { snackbarHostState.showSnackbar("Reset failed") }
                        }
                    },
                    modifier = Modifier.testTag("confirmResetButton")
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    state.pendingOverwriteFilename?.let { filename ->
        if (showOverwriteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showOverwriteDialog = false
                    state.clearPendingOverwrite()
                },
                title = { Text("Overwrite existing file?") },
                text = { Text("\"$filename\" already exists. Replace it with this workout?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showOverwriteDialog = false
                            state.save(overwrite = true, asCopy = state.pendingOverwriteAsCopy)
                                .onSuccess { onSaved() }
                                .onFailure { err ->
                                    scope.launch { snackbarHostState.showSnackbar(err.message ?: "Save failed") }
                                }
                        },
                        modifier = Modifier.testTag("confirmOverwriteButton")
                    ) { Text("Overwrite") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showOverwriteDialog = false
                            state.clearPendingOverwrite()
                        }
                    ) { Text("Cancel") }
                }
            )
        }
    }

    fun attemptSave(asCopy: Boolean = false) {
        attemptSave(
            state = state,
            scope = scope,
            snackbarHostState = snackbarHostState,
            viewRequesters = viewRequesters,
            onOverwriteDialog = { showOverwriteDialog = it },
            onSaved = onSaved,
            asCopy = asCopy
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.originalFilename == null) "New Workout" else "Edit Workout",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.isDirty) showDiscardDialog = true else onNavigateBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSeededFile) {
                        TextButton(onClick = { showResetDialog = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Reset")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isLandscape) {
                EditorFooter(
                    draftDurationSeconds = draft.totalDurationSeconds,
                    draftTss = draft.estimatedTss,
                    errorCount = errors.size,
                    warningCount = warnings.size,
                    errorMessages = footerErrors,
                    targetFilename = state.targetFilename(),
                    showSaveAsCopy = state.originalFilename != null,
                    onSave = { attemptSave() },
                    onSaveAsCopy = { attemptSave(asCopy = true) }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(detailScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WorkoutHeaderCard(state = state, issues = issues)
                    EditorFooter(
                        draftDurationSeconds = draft.totalDurationSeconds,
                        draftTss = draft.estimatedTss,
                        errorCount = errors.size,
                        warningCount = warnings.size,
                        errorMessages = footerErrors,
                        targetFilename = state.targetFilename(),
                        showSaveAsCopy = state.originalFilename != null,
                        onSave = { attemptSave() },
                        onSaveAsCopy = { attemptSave(asCopy = true) }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1.4f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SegmentList(
                        state = state,
                        issues = issues,
                        viewRequesters = viewRequesters
                    )
                    AddSegmentBar(onAdd = { state.addSegment(it) })
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WorkoutHeaderCard(state = state, issues = issues)
                SegmentList(
                    state = state,
                    issues = issues,
                    viewRequesters = viewRequesters
                )
                AddSegmentBar(onAdd = { state.addSegment(it) })
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun attemptSave(
    state: WorkoutEditorState,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    viewRequesters: Map<Long, BringIntoViewRequester>,
    onOverwriteDialog: (Boolean) -> Unit,
    onSaved: () -> Unit,
    asCopy: Boolean = false
) {
    state.save(overwrite = false, asCopy = asCopy)
        .onSuccess { onSaved() }
        .onFailure { err ->
            if (err is WorkoutEditorState.NeedsOverwrite) {
                onOverwriteDialog(true)
            } else {
                scope.launch { snackbarHostState.showSnackbar(err.message ?: "Save failed") }
            }
            val firstError = state.issues().firstOrNull { it.isError }
            if (firstError?.segmentIndex != null) {
                val failedId = state.segments.getOrNull(firstError.segmentIndex)?.id
                val requester = failedId?.let { viewRequesters[it] }
                if (requester != null) {
                    scope.launch { requester.bringIntoView() }
                }
            }
        }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SegmentList(
    state: WorkoutEditorState,
    issues: List<WorkoutValidator.ValidationIssue>,
    viewRequesters: MutableMap<Long, BringIntoViewRequester>
) {
    // Prune requesters for deleted rows so bring-into-view never targets a
    // detached card (subagent review m7).
    viewRequesters.keys.retainAll(state.segments.map { it.id }.toSet())
    state.segments.forEachIndexed { index, segment ->
        key(segment.id) {
            val requester = remember(segment.id) {
                BringIntoViewRequester().also { viewRequesters[segment.id] = it }
            }
            SegmentCard(
                index = index,
                segment = segment,
                issues = issues.filter { it.segmentIndex == index },
                isFirst = index == 0,
                isLast = index == state.segments.lastIndex,
                canDelete = state.segments.size > 1,
                modifier = Modifier.bringIntoViewRequester(requester),
                onUpdate = { transform -> state.updateSegment(index, transform) },
                onTypeChange = { type -> state.changeSegmentType(index, type) },
                onMoveUp = { state.moveSegment(index, index - 1) },
                onMoveDown = { state.moveSegment(index, index + 1) },
                onDuplicate = { state.duplicateSegment(index) },
                onDelete = { state.deleteSegment(index) },
                onDeleteCue = { rest, cue -> state.deleteCue(index, cue, rest) }
            )
        }
    }
}

@Composable
private fun WorkoutHeaderCard(
    state: WorkoutEditorState,
    issues: List<WorkoutValidator.ValidationIssue>
) {
    val nameError = issues.firstOrNull { it.field == WorkoutValidator.Field.NAME }?.message
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Workout Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.name,
                onValueChange = { state.updateHeader(name = it) },
                label = { Text("Name") },
                placeholder = { Text("e.g. Lunch Break HIIT") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editorNameField")
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = { state.updateHeader(description = it) },
                label = { Text("Description") },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.tagsText,
                onValueChange = { state.updateHeader(tagsText = it) },
                label = { Text("Tags (comma separated)") },
                placeholder = { Text("HIIT, Intervals") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SegmentCard(
    index: Int,
    segment: EditableSegment,
    issues: List<WorkoutValidator.ValidationIssue>,
    isFirst: Boolean,
    isLast: Boolean,
    canDelete: Boolean,
    onUpdate: ((EditableSegment) -> EditableSegment) -> Unit,
    onTypeChange: (EditableSegmentType) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDeleteCue: (Boolean, WorkoutTextEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val hasError = issues.any { it.isError }
    val containerColor = if (hasError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    fun messageFor(vararg fields: WorkoutValidator.Field): String? = issues.firstOrNull { it.field in fields }?.message

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Step ${index + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { typeMenuExpanded = true }) {
                        Text(segment.type.label, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        EditableSegmentType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                leadingIcon = if (type == segment.type) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF00E676)
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    typeMenuExpanded = false
                                    if (type != segment.type) onTypeChange(type)
                                }
                            )
                        }
                    }
                }
                Row {
                    IconButton(onClick = onMoveUp, enabled = !isFirst) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                    }
                    IconButton(onClick = onDelete, enabled = canDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFC62828))
                    }
                }
            }

            IntStepperField(
                label = "Duration (sec)",
                value = segment.durationSeconds,
                step = 15,
                min = WorkoutValidator.MIN_DURATION_SECONDS,
                error = messageFor(WorkoutValidator.Field.DURATION),
                hint = segment.durationSeconds.formatMmSs(),
                onValueChange = { newValue -> onUpdate { seg -> seg.copy(durationSeconds = newValue) } }
            )

            when (segment.type) {
                EditableSegmentType.STEADY_STATE -> {
                    IntStepperField(
                        label = "Power (%FTP)",
                        value = segment.powerPct,
                        step = 5,
                        min = 1,
                        error = messageFor(WorkoutValidator.Field.POWER),
                        onValueChange = { newValue -> onUpdate { seg -> seg.copy(powerPct = newValue) } }
                    )
                }
                EditableSegmentType.WARMUP,
                EditableSegmentType.COOLDOWN,
                EditableSegmentType.RAMP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntStepperField(
                            label = "Low (%FTP)",
                            value = segment.powerLowPct,
                            step = 5,
                            min = 1,
                            error = messageFor(
                                WorkoutValidator.Field.POWER_LOW,
                                WorkoutValidator.Field.POWER
                            ),
                            modifier = Modifier.weight(1f),
                            onValueChange = { newValue -> onUpdate { seg -> seg.copy(powerLowPct = newValue) } }
                        )
                        IntStepperField(
                            label = "High (%FTP)",
                            value = segment.powerHighPct,
                            step = 5,
                            min = 1,
                            error = messageFor(WorkoutValidator.Field.POWER_HIGH),
                            modifier = Modifier.weight(1f),
                            onValueChange = { newValue -> onUpdate { seg -> seg.copy(powerHighPct = newValue) } }
                        )
                    }
                }
                EditableSegmentType.FREE_RIDE,
                EditableSegmentType.MAX_EFFORT -> {
                    Text(
                        "ERG off — rider controls resistance",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                EditableSegmentType.INTERVALS -> {
                    IntStepperField(
                        label = "Repeats",
                        value = segment.repeatCount,
                        step = 1,
                        min = 1,
                        error = null,
                        hint = "expands to ${segment.repeatCount.coerceAtLeast(1) * 2} on/off steps",
                        onValueChange = { newValue -> onUpdate { seg -> seg.copy(repeatCount = newValue) } }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntStepperField(
                            label = "On (sec)",
                            value = segment.onDurationSeconds,
                            step = 5,
                            min = WorkoutValidator.MIN_DURATION_SECONDS,
                            error = null,
                            modifier = Modifier.weight(1f),
                            onValueChange = { newValue -> onUpdate { seg -> seg.copy(onDurationSeconds = newValue) } }
                        )
                        IntStepperField(
                            label = "On (%FTP)",
                            value = segment.onPowerPct,
                            step = 5,
                            min = 1,
                            error = null,
                            modifier = Modifier.weight(1f),
                            onValueChange = { newValue -> onUpdate { seg -> seg.copy(onPowerPct = newValue) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntStepperField(
                            label = "Off (sec)",
                            value = segment.offDurationSeconds,
                            step = 5,
                            min = WorkoutValidator.MIN_DURATION_SECONDS,
                            error = null,
                            modifier = Modifier.weight(1f),
                            onValueChange = { newValue -> onUpdate { seg -> seg.copy(offDurationSeconds = newValue) } }
                        )
                        IntStepperField(
                            label = "Off (%FTP)",
                            value = segment.offPowerPct,
                            step = 5,
                            min = 1,
                            error = null,
                            modifier = Modifier.weight(1f),
                            onValueChange = { newValue -> onUpdate { seg -> seg.copy(offPowerPct = newValue) } }
                        )
                    }
                }
            }

            if (segment.type == EditableSegmentType.INTERVALS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NullableIntStepperField(
                        label = "Work RPM",
                        value = segment.cadence,
                        step = 1,
                        min = WorkoutValidator.MIN_CADENCE_RPM,
                        error = null,
                        modifier = Modifier.weight(1f),
                        onValueChange = { newValue -> onUpdate { seg -> seg.copy(cadence = newValue) } }
                    )
                    NullableIntStepperField(
                        label = "Rest RPM",
                        value = segment.restingCadence,
                        step = 1,
                        min = WorkoutValidator.MIN_CADENCE_RPM,
                        error = null,
                        modifier = Modifier.weight(1f),
                        onValueChange = { newValue -> onUpdate { seg -> seg.copy(restingCadence = newValue) } }
                    )
                }
                // Interval rows validate as expanded pairs: surface row issues
                // here (field-level mapping is ambiguous across on/off steps).
                issues.forEach { issue ->
                    Text(issue.message, fontSize = 11.sp, color = errorColor(issue.isError))
                }
            } else {
                NullableIntStepperField(
                    label = "Cadence (RPM, empty = auto)",
                    value = segment.cadence,
                    step = 1,
                    min = WorkoutValidator.MIN_CADENCE_RPM,
                    error = messageFor(WorkoutValidator.Field.CADENCE),
                    onValueChange = { newValue -> onUpdate { seg -> seg.copy(cadence = newValue) } }
                )
            }

            if (segment.cues.isNotEmpty() || (segment.type == EditableSegmentType.INTERVALS && segment.restCues.isNotEmpty())) {
                Text("Coaching cues (tap ⊗ to remove):", fontSize = 11.sp, color = Color.Gray)
                segment.cues.forEach { cue ->
                    CueRow(message = cue.message, onDelete = { onDeleteCue(false, cue) })
                }
                if (segment.type == EditableSegmentType.INTERVALS) {
                    segment.restCues.forEach { cue ->
                        CueRow(message = "Rest: ${cue.message}", onDelete = { onDeleteCue(true, cue) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CueRow(message: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            message,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete cue",
                tint = Color(0xFFC62828),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun errorColor(isError: Boolean): Color = if (isError) MaterialTheme.colorScheme.error else Color(0xFFFFB300)

@Composable
private fun AddSegmentBar(onAdd: (EditableSegmentType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add step")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EditableSegmentType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        expanded = false
                        onAdd(type)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorFooter(
    draftDurationSeconds: Int,
    draftTss: Double,
    errorCount: Int,
    warningCount: Int,
    errorMessages: List<String>,
    targetFilename: String,
    showSaveAsCopy: Boolean,
    onSave: () -> Unit,
    onSaveAsCopy: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total ${draftDurationSeconds.formatMmSs()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text("TSS ~%.0f".format(draftTss), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text("Saves as $targetFilename", fontSize = 12.sp, color = Color.Gray)
            if (errorCount > 0) {
                errorMessages.forEach { message ->
                    Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                if (errorCount > errorMessages.size) {
                    Text(
                        "+${errorCount - errorMessages.size} more",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (warningCount > 0) {
                Text("$warningCount warning${if (warningCount == 1) "" else "s"} (saving allowed)", fontSize = 12.sp, color = Color(0xFFFFB300))
            } else {
                Text("Ready to save", fontSize = 12.sp, color = Color(0xFF00E676))
            }
            Button(
                onClick = onSave,
                enabled = errorCount == 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("editorSaveButton")
            ) {
                Text("Save Workout", fontWeight = FontWeight.Black, color = Color.Black)
            }
            if (showSaveAsCopy) {
                OutlinedButton(
                    onClick = onSaveAsCopy,
                    enabled = errorCount == 0,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save as copy", fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Numeric field with −/+ steppers. Typed input passes through unclamped so
 * validation (not the text field) reports out-of-range values; the −
 * stepper button clamps at [min] as a convenience.
 */
@Composable
private fun IntStepperField(
    label: String,
    value: Int,
    step: Int,
    min: Int?,
    error: String?,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val localInvalid = text.isNotBlank() && text.toIntOrNull() == null
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    val next = value - step
                    onValueChange(if (min != null) next.coerceAtLeast(min) else next)
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("−") }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    input.toIntOrNull()?.let { onValueChange(it) }
                },
                label = { Text(label, fontSize = 11.sp) },
                isError = error != null || localInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            OutlinedButton(
                onClick = { onValueChange(value + step) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("+") }
        }
        val message = error ?: if (localInvalid) "Enter a whole number" else hint
        message?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = if (error != null || localInvalid) MaterialTheme.colorScheme.error else Color.Gray
            )
        }
    }
}

@Composable
private fun NullableIntStepperField(
    label: String,
    value: Int?,
    step: Int,
    min: Int,
    error: String?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    val localInvalid = text.isNotBlank() && text.toIntOrNull() == null
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onValueChange(((value ?: 85) - step).coerceAtLeast(min)) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("−") }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    if (input.isBlank()) {
                        onValueChange(null)
                    } else {
                        input.toIntOrNull()?.let { onValueChange(it) }
                    }
                },
                label = { Text(label, fontSize = 11.sp) },
                isError = error != null || localInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            OutlinedButton(
                onClick = { onValueChange((value ?: 85) + step) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("+") }
        }
        val message = error ?: if (localInvalid) "Enter a whole number" else null
        message?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun Int.formatMmSs(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "%d:%02d".format(minutes, seconds)
}
