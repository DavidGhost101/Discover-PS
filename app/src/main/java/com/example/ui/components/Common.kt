package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.Remote
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.DiscoveryGreenDark
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.StatusMessage
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------- Layout scaffolding shared by every role portal ----------

@Composable
fun PortalHeader(
    title: String,
    badge: String,
    badgeColor: Color,
    subtitle: String,
    icon: ImageVector,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DiscoveryGreenDark)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(badgeColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Box(
                            modifier = Modifier
                                .background(badgeColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(badge, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                }
            }
            IconButton(onClick = onSignOut, modifier = Modifier.testTag("header_sign_out_button")) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = Color.White)
            }
        }
    }
}

@Composable
fun <T : Enum<T>> PortalTabs(tabs: List<T>, current: T, title: (T) -> String, onSelect: (T) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOf(current).coerceAtLeast(0),
        containerColor = Color.White,
        contentColor = DiscoveryGreen,
        edgePadding = 8.dp
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = current == tab,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = title(tab),
                        fontWeight = if (current == tab) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
fun StatusBanner(message: StatusMessage?, onDismiss: () -> Unit) {
    if (message == null) return
    val fg = if (message.isError) Color(0xFFB91C1C) else Color(0xFF2E7D32)
    val bg = if (message.isError) Color(0xFFFEE2E2) else Color(0xFFE8F5E9)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(if (message.isError) "status_error" else "status_success"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(message.text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen, modifier = modifier)
}

@Composable
fun LoadingState(text: String = "Loading…") {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(color = DiscoveryGreen, modifier = Modifier.size(28.dp))
        Text(text, fontSize = 12.sp, color = SchoolSlate)
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(28.dp).testTag("empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Inbox, contentDescription = null, tint = SchoolSlate.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
        Text(text, fontSize = 13.sp, color = SchoolSlate, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().testTag("error_state")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFDC2626))
            Text(message, fontSize = 12.sp, color = Color(0xFF991B1B))
        }
    }
}

/** Renders loading / error / empty / content for a live collection. */
@Composable
fun <T> RemoteContent(
    remote: Remote<T>,
    emptyText: String,
    items: List<T> = remote.items,
    content: @Composable (List<T>) -> Unit
) {
    when {
        remote.error != null -> ErrorState(remote.error)
        remote.loading && items.isEmpty() -> LoadingState()
        items.isEmpty() -> EmptyState(emptyText)
        else -> content(items)
    }
}

@Composable
fun InfoCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = SchoolSlate)
        Spacer(Modifier.width(12.dp))
        Text(value.ifBlank { "—" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B), textAlign = TextAlign.End)
    }
}

// ---------- Form building blocks ----------

/** A dialog whose confirm button is disabled while saving and which shows the real error. */
@Composable
fun FormDialog(
    title: String,
    confirmText: String,
    saving: Boolean,
    error: String?,
    canSubmit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error != null) {
                    Text(
                        error,
                        color = Color(0xFFDC2626),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEE2E2), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                            .testTag("form_error")
                    )
                }
                content()
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canSubmit && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                modifier = Modifier.testTag("form_confirm_button")
            ) {
                if (saving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    error: String? = null,
    minLines: Int = 1,
    singleLine: Boolean = minLines == 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

data class Choice(val id: String, val label: String)

@Composable
fun DropdownSelector(
    label: String,
    choices: List<Choice>,
    selectedId: String,
    onSelect: (String) -> Unit,
    tag: String,
    placeholder: String = "Select…"
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { it.id == selectedId }
    Column {
        Text(label, fontSize = 11.sp, color = SchoolSlate)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().testTag(tag),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(selected?.label ?: placeholder, modifier = Modifier.weight(1f), color = Color(0xFF1E293B))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (choices.isEmpty()) {
                    DropdownMenuItem(text = { Text("Nothing to choose yet") }, onClick = { expanded = false }, enabled = false)
                }
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label) },
                        onClick = { onSelect(choice.id); expanded = false },
                        modifier = Modifier.testTag("${tag}_option_${choice.id}")
                    )
                }
            }
        }
    }
}

@Composable
fun MultiSelectList(
    label: String,
    choices: List<Choice>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    tag: String,
    emptyText: String = "Nothing available yet."
) {
    Column {
        Text(label, fontSize = 11.sp, color = SchoolSlate, fontWeight = FontWeight.SemiBold)
        if (choices.isEmpty()) {
            Text(emptyText, fontSize = 11.sp, color = SchoolSlate)
        }
        choices.forEach { choice ->
            val checked = choice.id in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onChange(if (checked) selected - choice.id else selected + choice.id) }
                    .testTag("${tag}_${choice.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = { onChange(if (it) selected + choice.id else selected - choice.id) })
                Text(choice.label, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ChoiceChips(choices: List<Choice>, selectedId: String, onSelect: (String) -> Unit, tag: String) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(choices.size) { index ->
            val choice = choices[index]
            val isSelected = choice.id == selectedId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) DiscoveryGreen else Color.White)
                    .clickable { onSelect(choice.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("${tag}_${choice.id}")
            ) {
                Text(choice.label, fontSize = 11.sp, color = if (isSelected) Color.White else SchoolSlate, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Date field backed by the Material date picker. Stores the chosen day at 23:59 local time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDateField(value: Timestamp?, onChange: (Timestamp?) -> Unit, tag: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Text("Due date", fontSize = 11.sp, color = SchoolSlate)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(value?.let { formatDate(it) } ?: "No due date", modifier = Modifier.weight(1f))
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value?.toDate()?.let { toUtcMidnight(it) })
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.selectedDateMillis?.let { endOfLocalDay(it) })
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { onChange(null); open = false }) { Text("Clear") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

private fun toUtcMidnight(date: Date): Long {
    val local = Calendar.getInstance().apply { time = date }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun endOfLocalDay(utcMillis: Long): Timestamp {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val local = Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 0)
    }
    return Timestamp(local.time)
}

fun formatDate(timestamp: Timestamp?): String =
    timestamp?.toDate()?.let { SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(it) } ?: "No due date"

fun formatDateTime(timestamp: Timestamp?): String =
    timestamp?.toDate()?.let { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(it) } ?: ""

// ---------- Profile (shared by every role) ----------

@Composable
fun ProfileCard(
    name: String,
    email: String,
    phone: String,
    roleLabel: String,
    accent: Color,
    saving: Boolean,
    error: String?,
    details: List<Pair<String, String>>,
    onSave: (name: String, phone: String, onDone: () -> Unit) -> Unit,
    onResetPassword: () -> Unit,
    onSignOut: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable(name) { mutableStateOf(name) }
    var editPhone by rememberSaveable(phone) { mutableStateOf(phone) }
    val nameError = if (editName.isBlank()) "Name is required." else null
    val phoneError = com.example.domain.FormValidation.phone(editPhone)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Text(name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Text(name.ifBlank { "Name not set" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.testTag("profile_name"))
                if (email.isNotBlank()) Text(email, fontSize = 12.sp, color = SchoolSlate)
                Pill(roleLabel, accent)
            }
        }

        InfoCard {
            Text("Account details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            LabeledValue("Phone", phone)
            details.forEach { (label, value) -> LabeledValue(label, value) }
        }

        if (editing) {
            InfoCard {
                Text("Edit profile", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (error != null) Text(error, color = Color(0xFFDC2626), fontSize = 12.sp)
                FormField(editName, { editName = it }, "Full name", "profile_name_input", nameError)
                FormField(editPhone, { editPhone = it }, "Phone number", "profile_phone_input", phoneError)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = false; editName = name; editPhone = phone }, enabled = !saving) { Text("Cancel") }
                    Button(
                        onClick = { onSave(editName, editPhone) { editing = false } },
                        enabled = !saving && nameError == null && phoneError == null,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                        modifier = Modifier.testTag("profile_save_button")
                    ) { Text(if (saving) "Saving…" else "Save") }
                }
            }
        } else {
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().testTag("profile_edit_button")) {
                Text("Edit name & phone")
            }
        }

        if (email.isNotBlank()) {
            OutlinedButton(onClick = onResetPassword, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("profile_reset_password_button")) {
                Text("Change password (email me a secure link)")
            }
        }

        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("profile_sign_out_button")
        ) {
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $what?", fontWeight = FontWeight.Bold) },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier.testTag("confirm_delete_button")
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
