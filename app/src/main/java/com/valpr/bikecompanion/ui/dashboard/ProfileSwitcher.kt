package com.valpr.bikecompanion.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valpr.bikecompanion.data.Profile

@Composable
fun ProfileAvatar(profile: Profile?, modifier: Modifier = Modifier, sizeDp: Int = 32) {
    val bg = profile?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
    val initial = profile?.let { Profile.initialFor(it.name) } ?: "?"
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(bg)
    ) {
        Text(initial, fontWeight = FontWeight.Black, fontSize = (sizeDp * 0.45).sp, color = Color.Black)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcher(
    profiles: List<Profile>,
    activeProfile: Profile?,
    sessionBlocked: Boolean,
    onSwitch: (String) -> Unit,
    onCreate: (String, Int) -> Unit,
    onRename: (String, String) -> Unit,
    onColor: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var deletingProfile by remember { mutableStateOf<Profile?>(null) }

    // Top-bar avatar chip (owner: switcher only).
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { showSheet = true }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(activeProfile, sizeDp = 32)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                activeProfile?.name ?: "Rider",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Who's riding?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (sessionBlocked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Switching is disabled mid-ride — end or discard the current ride first.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                profiles.forEach { profile ->
                    val isActive = profile.id == activeProfile?.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !sessionBlocked && !isActive) { onSwitch(profile.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileAvatar(profile, sizeDp = 36)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    if (isActive) "Active" else "Tap to switch",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            if (isActive) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { editingProfile = profile }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename ${profile.name}")
                            }
                            if (profiles.size > 1) {
                                IconButton(onClick = { deletingProfile = profile }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ${profile.name}")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showCreate = true },
                    enabled = profiles.size < Profile.MAX_PROFILES && !sessionBlocked,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (profiles.size >= Profile.MAX_PROFILES) {
                            "Max ${Profile.MAX_PROFILES} profiles"
                        } else {
                            "Add profile"
                        },
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Workouts are shared. History, favorites, settings and Health Connect sync are per-profile.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCreate) {
        ProfileEditDialog(
            title = "Add profile",
            initialName = "",
            initialColor = Profile.DEFAULT_COLORS[profiles.size % Profile.DEFAULT_COLORS.size],
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name, color ->
                onCreate(name, color)
                showCreate = false
            }
        )
    }

    editingProfile?.let { target ->
        ProfileEditDialog(
            title = "Edit profile",
            initialName = target.name,
            initialColor = target.colorArgb,
            confirmLabel = "Save",
            onDismiss = { editingProfile = null },
            onConfirm = { name, color ->
                if (name.trim() != target.name) onRename(target.id, name)
                if (color != target.colorArgb) onColor(target.id, color)
                editingProfile = null
            }
        )
    }

    deletingProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text("Delete ${target.name}?") },
            text = {
                Text("This permanently deletes ${target.name}'s settings and ride history on this device. Shared workouts stay. This can't be undone.")
            },
            confirmButton = {
                Button(onClick = {
                    onDelete(target.id)
                    deletingProfile = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingProfile = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileEditDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Profile.MAX_NAME_LENGTH + 4) },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Alex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Profile.DEFAULT_COLORS) { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c }
                                .let {
                                    if (selected) it.padding(2.dp) else it
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, color) },
                enabled = Profile.sanitizeName(name) != null
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
