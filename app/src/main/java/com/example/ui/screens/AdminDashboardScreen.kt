package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Assignment
import com.example.data.model.Notice
import com.example.data.model.NoticeAudience
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolSettings
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.Teacher
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.Remote
import com.example.domain.FormValidation
import com.example.domain.SchoolAccess
import com.example.ui.components.Choice
import com.example.ui.components.ChoiceChips
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DropdownSelector
import com.example.ui.components.DueDateField
import com.example.ui.components.ErrorState
import com.example.ui.components.FormDialog
import com.example.ui.components.FormField
import com.example.ui.components.InfoCard
import com.example.ui.components.LabeledValue
import com.example.ui.components.MultiSelectList
import com.example.ui.components.Pill
import com.example.ui.components.PortalHeader
import com.example.ui.components.PortalTabs
import com.example.ui.components.RemoteContent
import com.example.ui.components.StatusBanner
import com.example.ui.components.formatDate
import com.example.ui.components.formatDateTime
import com.example.ui.theme.DiscoveryGold
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.AdminTab
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.example.ui.viewmodel.SchoolState

private val Danger = Color(0xFFEF4444)

@Composable
fun AdminDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.adminTab.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val state by viewModel.school.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        PortalHeader(
            title = if (user.isProtectedOwner) "Owner Console" else "Admin Console",
            badge = if (user.isProtectedOwner) "OWNER" else "ADMIN",
            badgeColor = DiscoveryGold,
            subtitle = user.label,
            icon = Icons.Default.AdminPanelSettings,
            onSignOut = { viewModel.signOut() }
        )
        StatusBanner(statusMsg) { viewModel.clearStatusMessage() }
        PortalTabs(AdminTab.entries, currentTab, { it.title }) { viewModel.setAdminTab(it) }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (currentTab) {
                AdminTab.DASHBOARD -> AdminOverviewTab(state, viewModel)
                AdminTab.USERS -> AdminUsersTab(state, user, viewModel)
                AdminTab.TEACHERS -> AdminTeachersTab(state, viewModel)
                AdminTab.STUDENTS -> AdminStudentsTab(state, viewModel)
                AdminTab.CLASSES -> AdminClassesTab(state, viewModel)
                AdminTab.SUBJECTS -> AdminSubjectsTab(state, viewModel)
                AdminTab.ASSIGNMENTS -> AdminAssignmentsTab(state, viewModel)
                AdminTab.NOTICES -> AdminNoticesTab(state, viewModel)
                AdminTab.SETTINGS -> AdminSettingsTab(state, viewModel)
            }
        }
    }
}

// ---------- shared helpers ----------

private fun <T> countOf(remote: Remote<T>, predicate: (T) -> Boolean = { true }): String = when {
    remote.error != null -> "!"
    remote.loading -> "…"
    else -> remote.items.count(predicate).toString()
}

private fun classChoices(state: SchoolState) = state.classes.items.sortedBy { it.name }.map { Choice(it.id, "${it.name} (${it.grade})") }
private fun subjectChoices(state: SchoolState) = state.subjects.items.sortedBy { it.name }.map { Choice(it.id, it.name) }
private fun teacherChoices(state: SchoolState) = state.teachers.items.sortedBy { it.name }.map { Choice(it.uid, it.name) }
private fun usersWithRole(state: SchoolState, role: UserRole) =
    state.users.items.filter { it.role == role.name }.sortedBy { it.label }

@Composable
private fun TabHeader(title: String, actionText: String?, actionTag: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (actionText != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag(actionTag)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SchoolSlate) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun RowActions(onEdit: (() -> Unit)?, onDelete: (() -> Unit)?, tagSuffix: String) {
    Row {
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_$tagSuffix")) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DiscoveryGreen)
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_$tagSuffix")) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Danger)
            }
        }
    }
}

// ---------- Overview ----------

@Composable
private fun AdminOverviewTab(state: SchoolState, viewModel: SchoolAuthViewModel) {
    val go: (AdminTab) -> Unit = { viewModel.setAdminTab(it) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("School Operations Snapshot", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DiscoveryGreen)
        listOfNotNull(state.users.error, state.students.error, state.notices.error).firstOrNull()?.let { ErrorState(it) }

        val pending = state.users.items.count { !it.active && it.role == UserRole.PARENT.name && it.createdAt == it.updatedAt }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard("User accounts", countOf(state.users), Icons.Default.People, DiscoveryGreen, Modifier.weight(1f), "stat_users") { go(AdminTab.USERS) }
            StatMetricCard("Parents", countOf(state.users) { it.role == UserRole.PARENT.name }, Icons.Default.FamilyRestroom, Color(0xFF7C3AED), Modifier.weight(1f), "stat_parents") { go(AdminTab.USERS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard("Teachers", countOf(state.teachers), Icons.Default.School, DiscoveryGreen, Modifier.weight(1f), "stat_teachers") { go(AdminTab.TEACHERS) }
            StatMetricCard("Students", countOf(state.students), Icons.Default.People, DiscoveryGold, Modifier.weight(1f), "stat_students") { go(AdminTab.STUDENTS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard("Classes", countOf(state.classes), Icons.Default.Class, Color(0xFF2563EB), Modifier.weight(1f), "stat_classes") { go(AdminTab.CLASSES) }
            StatMetricCard("Subjects", countOf(state.subjects), Icons.Default.Book, Color(0xFF7C3AED), Modifier.weight(1f), "stat_subjects") { go(AdminTab.SUBJECTS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard("Assignments", countOf(state.assignments), Icons.Default.Assignment, Color(0xFF059669), Modifier.weight(1f), "stat_assignments") { go(AdminTab.ASSIGNMENTS) }
            StatMetricCard("Notices", countOf(state.notices), Icons.Default.Campaign, Color(0xFFDC2626), Modifier.weight(1f), "stat_notices") { go(AdminTab.NOTICES) }
        }
        StatMetricCard(
            "Inactive / awaiting approval",
            countOf(state.users) { !it.active },
            Icons.Default.HourglassTop,
            if (pending > 0) Color(0xFFB45309) else SchoolSlate,
            Modifier.fillMaxWidth(),
            "stat_inactive"
        ) { go(AdminTab.USERS) }

        InfoCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Latest School Notices", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = { go(AdminTab.NOTICES) }) { Text("View All", fontSize = 12.sp, color = DiscoveryGreen) }
            }
            val latest = state.notices.items.sortedByDescending { it.createdAt }.take(3)
            if (latest.isEmpty() && !state.notices.loading) Text("No notices yet.", fontSize = 12.sp, color = SchoolSlate)
            latest.forEach { notice ->
                Text(notice.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(notice.message, fontSize = 11.sp, color = SchoolSlate, maxLines = 2)
                HorizontalDivider(color = Color(0xFFF1F5F9))
            }
        }
    }
}

@Composable
fun StatMetricCard(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.testTag(tag)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), modifier = Modifier.testTag("${tag}_value"))
                Text(text = title, fontSize = 11.sp, color = SchoolSlate)
            }
        }
    }
}

// ---------- Users & parents ----------

@Composable
private fun AdminUsersTab(state: SchoolState, me: User, viewModel: SchoolAuthViewModel) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<User?>(null) }
    var creating by remember { mutableStateOf(false) }

    val filters = listOf(Choice("ALL", "All"), Choice("INACTIVE", "Inactive / pending")) +
        UserRole.entries.map { Choice(it.name, it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
    val filtered = state.users.items.filter { u ->
        val matchesFilter = when (filter) {
            "ALL" -> true
            "INACTIVE" -> !u.active
            else -> u.role == filter
        }
        matchesFilter && (search.isBlank() || listOf(u.displayName, u.email, u.phoneNumber).any { it.contains(search, ignoreCase = true) })
    }.sortedWith(compareBy<User>({ it.active }, { it.label }))

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabHeader("User Accounts (${state.users.items.size})", "Create Account", "admin_create_account_button") {
            viewModel.clearFormError(); creating = true
        }
        ChoiceChips(filters, filter, { filter = it }, "user_filter")
        SearchField(search, { search = it }, "Search name, email or phone…", "admin_user_search")
        RemoteContent(state.users, if (search.isBlank() && filter == "ALL") "No user accounts yet." else "No users match.", filtered) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.uid }) { u ->
                    val children = if (u.role == UserRole.PARENT.name) SchoolAccess.childrenOf(u.uid, state.students.items) else emptyList()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.clearFormError(); editing = u }.testTag("user_row_${u.uid}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(u.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (u.isProtectedOwner) Pill("PROTECTED OWNER", DiscoveryGold) else Pill(u.role, DiscoveryGreen)
                                Pill(if (u.active) "ACTIVE" else "INACTIVE", if (u.active) Color(0xFF059669) else Color(0xFFB45309))
                            }
                            Text(listOf(u.email, u.phoneNumber).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 11.sp, color = SchoolSlate)
                            if (u.role == UserRole.PARENT.name) {
                                Text(
                                    if (children.isEmpty()) "No linked children" else "Children: " + children.joinToString { it.fullName },
                                    fontSize = 11.sp, color = Color(0xFF7C3AED)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) CreateAccountDialog(viewModel, onDismiss = { creating = false })
    editing?.let { u -> EditUserDialog(u, me, state, viewModel, onDismiss = { editing = null }) }
}

@Composable
private fun CreateAccountDialog(viewModel: SchoolAuthViewModel, onDismiss: () -> Unit, fixedRole: UserRole? = null) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(fixedRole?.name ?: UserRole.PARENT.name) }
    val emailError = if (email.isNotEmpty()) FormValidation.email(email) else null
    val phoneError = FormValidation.phone(phone)

    FormDialog(
        title = "Create Account",
        confirmText = "Create & email link",
        saving = saving,
        error = error,
        canSubmit = name.isNotBlank() && email.isNotBlank() && emailError == null && phoneError == null,
        onDismiss = onDismiss,
        onConfirm = { viewModel.createAccount(email, name, UserRole.valueOf(role), phone, onDismiss) }
    ) {
        Text(
            "The person receives an email to set their own password. Use the Teachers tab to create teachers with class assignments.",
            fontSize = 11.sp, color = SchoolSlate
        )
        FormField(name, { name = it }, "Full name", "account_name_input")
        FormField(email, { email = it }, "Email", "account_email_input", emailError)
        FormField(phone, { phone = it }, "Phone (optional)", "account_phone_input", phoneError)
        if (fixedRole == null) {
            DropdownSelector("Role", FormValidation.assignableRoles.map { Choice(it.name, it.name) }, role, { role = it }, "account_role_select")
        }
    }
}

@Composable
private fun EditUserDialog(user: User, me: User, state: SchoolState, viewModel: SchoolAuthViewModel, onDismiss: () -> Unit) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var name by remember { mutableStateOf(user.displayName) }
    var phone by remember { mutableStateOf(user.phoneNumber) }
    var role by remember { mutableStateOf(user.role) }
    var active by remember { mutableStateOf(user.active) }
    val isMe = user.uid == me.uid
    val children = SchoolAccess.childrenOf(user.uid, state.students.items)
    val phoneError = FormValidation.phone(phone)

    FormDialog(
        title = "Manage ${user.label}",
        confirmText = "Save",
        saving = saving,
        error = error,
        canSubmit = name.isNotBlank() && phoneError == null,
        onDismiss = onDismiss,
        onConfirm = { viewModel.updateUser(user.copy(displayName = name.trim(), phoneNumber = phone.trim(), role = role, active = active), onDismiss) }
    ) {
        LabeledValue("Email", user.email)
        LabeledValue("Created", formatDateTime(user.createdAt))
        FormField(name, { name = it }, "Display name", "edit_user_name_input")
        FormField(phone, { phone = it }, "Phone", "edit_user_phone_input", phoneError)
        if (user.isProtectedOwner) {
            Text(
                "Protected Super Admin Owner account. Its role, status and deletion cannot be changed by any " +
                    "administrator; attempts are refused by the server and recorded in the audit log.",
                fontSize = 11.sp, color = Color(0xFFB91C1C), modifier = Modifier.testTag("owner_protected_note")
            )
        }
        if (isMe) {
            Text("You cannot change your own role or deactivate yourself.", fontSize = 11.sp, color = SchoolSlate)
        } else {
            DropdownSelector("Role", FormValidation.assignableRoles.map { Choice(it.name, it.name) }, role, { role = it }, "edit_user_role_select")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { active = !active }.testTag("edit_user_active_toggle")) {
                Checkbox(checked = active, onCheckedChange = { active = it })
                Text(if (user.active) "Account active" else "Approve / activate account", fontSize = 13.sp)
            }
        }
        if (user.role == UserRole.PARENT.name) {
            Text("Linked children", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            if (children.isEmpty()) Text("None. Link children from the Students tab (edit a learner → Parents).", fontSize = 11.sp, color = SchoolSlate)
            children.forEach { child ->
                val cls = state.classes.items.firstOrNull { it.id == child.classId }
                Text("• ${child.fullName} — ${cls?.name ?: "No class"}", fontSize = 12.sp)
            }
        }
        if (user.email.isNotBlank()) {
            OutlinedButton(onClick = { viewModel.sendPasswordResetFor(user) }, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("edit_user_reset_button")) {
                Text("Email a password reset link")
            }
        }
    }
}

// ---------- Teachers ----------

@Composable
private fun AdminTeachersTab(state: SchoolState, viewModel: SchoolAuthViewModel) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Teacher?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Teacher?>(null) }
    val usersById = state.users.items.associateBy { it.uid }
    val filtered = state.teachers.items.filter {
        search.isBlank() || listOf(it.name, it.email, it.employeeNumber).any { v -> v.contains(search, ignoreCase = true) }
    }.sortedBy { it.name }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabHeader("Teachers (${state.teachers.items.size})", "Add Teacher", "admin_add_teacher_button") { viewModel.clearFormError(); creating = true }
        SearchField(search, { search = it }, "Search by name, employee # or email…", "admin_teacher_search")
        RemoteContent(state.teachers, if (search.isBlank()) "No teachers yet. Tap \"Add Teacher\" to create one." else "No teachers match.", filtered) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.uid }) { teacher ->
                    val classNames = state.classes.items.filter { it.id in teacher.classIds }.map { it.name }
                    val subjectNames = state.subjects.items.filter { it.id in teacher.subjectIds }.map { it.name }
                    val account = usersById[teacher.uid]
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Emp #: ${teacher.employeeNumber.ifBlank { "—" }} • ${teacher.email}", fontSize = 11.sp, color = SchoolSlate)
                            }
                            RowActions({ viewModel.clearFormError(); editing = teacher }, { deleting = teacher }, "teacher_${teacher.uid}")
                        }
                        Text("Classes: ${classNames.joinToString().ifBlank { "none assigned" }}", fontSize = 11.sp, color = Color(0xFF2563EB))
                        Text("Subjects: ${subjectNames.joinToString().ifBlank { "none assigned" }}", fontSize = 11.sp, color = Color(0xFF7C3AED))
                        Pill(
                            when {
                                account == null -> "NO LOGIN"
                                account.active -> "LOGIN ACTIVE"
                                else -> "LOGIN INACTIVE"
                            },
                            if (account?.active == true) Color(0xFF059669) else Color(0xFFB45309)
                        )
                    }
                }
            }
        }
    }

    if (creating) TeacherDialog(null, state, viewModel) { creating = false }
    editing?.let { t -> TeacherDialog(t, state, viewModel) { editing = null } }
    deleting?.let { t ->
        ConfirmDeleteDialog("teacher record for ${t.name} (their login stays; deactivate it under Users)", { viewModel.deleteTeacher(t) }, { deleting = null })
    }
}

@Composable
private fun TeacherDialog(existing: Teacher?, state: SchoolState, viewModel: SchoolAuthViewModel, onDismiss: () -> Unit) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var firstName by remember { mutableStateOf(existing?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(existing?.lastName.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    var empNo by remember { mutableStateOf(existing?.employeeNumber.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phoneNumber.orEmpty()) }
    var classIds by remember { mutableStateOf(existing?.classIds.orEmpty().toSet()) }
    var subjectIds by remember { mutableStateOf(existing?.subjectIds.orEmpty().toSet()) }
    val emailError = if (email.isNotEmpty()) FormValidation.email(email) else null
    val phoneError = FormValidation.phone(phone)

    FormDialog(
        title = if (existing == null) "Add Teacher" else "Edit ${existing.name}",
        confirmText = if (existing == null) "Create teacher" else "Save",
        saving = saving,
        error = error,
        canSubmit = firstName.isNotBlank() && email.isNotBlank() && emailError == null && phoneError == null,
        onDismiss = onDismiss,
        onConfirm = {
            val teacher = (existing ?: Teacher()).copy(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                email = email.trim(),
                employeeNumber = empNo.trim(),
                phoneNumber = phone.trim(),
                classIds = classIds.toList(),
                subjectIds = subjectIds.toList()
            )
            if (existing == null) viewModel.createTeacher(teacher, onDismiss) else viewModel.saveTeacher(teacher, existing, onDismiss)
        }
    ) {
        if (existing == null) {
            Text("Creates the teacher's login and emails them a link to set a password.", fontSize = 11.sp, color = SchoolSlate)
        }
        FormField(firstName, { firstName = it }, "First name", "teacher_first_name_input")
        FormField(lastName, { lastName = it }, "Last name", "teacher_last_name_input")
        if (existing == null) FormField(email, { email = it }, "Email (login)", "teacher_email_input", emailError)
        else LabeledValue("Login email", existing.email)
        FormField(empNo, { empNo = it }, "Employee number", "teacher_emp_input")
        FormField(phone, { phone = it }, "Phone", "teacher_phone_input", phoneError)
        MultiSelectList("Classes taught", classChoices(state), classIds, { classIds = it }, "teacher_class", "Create classes first.")
        MultiSelectList("Subjects taught", subjectChoices(state), subjectIds, { subjectIds = it }, "teacher_subject", "Create subjects first.")
    }
}

// ---------- Students ----------

@Composable
private fun AdminStudentsTab(state: SchoolState, viewModel: SchoolAuthViewModel) {
    var search by remember { mutableStateOf("") }
    var classFilter by remember { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<Student?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Student?>(null) }
    var addingParentFor by remember { mutableStateOf<Student?>(null) }
    var addingLoginFor by remember { mutableStateOf<Student?>(null) }
    val usersById = state.users.items.associateBy { it.uid }
    val classesById = state.classes.items.associateBy { it.id }

    val filtered = state.students.items.filter { s ->
        (classFilter == "ALL" || s.classId == classFilter) &&
            (search.isBlank() || s.fullName.contains(search, ignoreCase = true) || s.studentNumber.contains(search, ignoreCase = true))
    }.sortedBy { it.fullName }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabHeader("Students (${state.students.items.size})", "Enroll Student", "admin_add_student_button") { viewModel.clearFormError(); creating = true }
        ChoiceChips(listOf(Choice("ALL", "All Classes")) + state.classes.items.sortedBy { it.name }.map { Choice(it.id, it.name) }, classFilter, { classFilter = it }, "student_class_filter")
        SearchField(search, { search = it }, "Search learner name or admission #…", "admin_student_search")
        RemoteContent(state.students, if (search.isBlank() && classFilter == "ALL") "No learners enrolled yet." else "No learners match.", filtered) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.uid }) { student ->
                    val parents = student.parentIds.map { usersById[it]?.label ?: "Unknown account" }
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(student.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Pill(classesById[student.classId]?.name ?: "No class", Color(0xFFB45309))
                                }
                                Text("No: ${student.studentNumber.ifBlank { "—" }}", fontSize = 11.sp, color = SchoolSlate)
                            }
                            RowActions({ viewModel.clearFormError(); editing = student }, { deleting = student }, "student_${student.uid}")
                        }
                        Text("Parents: ${parents.joinToString().ifBlank { "none linked" }}", fontSize = 11.sp, color = Color(0xFF7C3AED))
                        Text(
                            "Learner login: ${if (student.userId.isBlank()) "none" else usersById[student.userId]?.label ?: "linked"}",
                            fontSize = 11.sp, color = SchoolSlate
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.clearFormError(); addingParentFor = student }, modifier = Modifier.testTag("add_parent_${student.uid}")) {
                                Text("+ Parent account", fontSize = 12.sp)
                            }
                            if (student.userId.isBlank()) {
                                TextButton(onClick = { viewModel.clearFormError(); addingLoginFor = student }, modifier = Modifier.testTag("add_login_${student.uid}")) {
                                    Text("+ Learner login", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) StudentDialog(null, state, viewModel) { creating = false }
    editing?.let { s -> StudentDialog(s, state, viewModel) { editing = null } }
    deleting?.let { s -> ConfirmDeleteDialog("learner ${s.fullName}", { viewModel.deleteStudent(s) }, { deleting = null }) }
    addingParentFor?.let { s -> NewParentDialog(s, viewModel) { addingParentFor = null } }
    addingLoginFor?.let { s -> NewLearnerLoginDialog(s, viewModel) { addingLoginFor = null } }
}

@Composable
private fun StudentDialog(existing: Student?, state: SchoolState, viewModel: SchoolAuthViewModel, onDismiss: () -> Unit) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var firstName by remember { mutableStateOf(existing?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(existing?.lastName.orEmpty()) }
    var studentNo by remember { mutableStateOf(existing?.studentNumber.orEmpty()) }
    var email by remember { mutableStateOf(existing?.email.orEmpty()) }
    var classId by remember { mutableStateOf(existing?.classId.orEmpty()) }
    var parentIds by remember { mutableStateOf(existing?.parentIds.orEmpty().toSet()) }
    var userId by remember { mutableStateOf(existing?.userId.orEmpty()) }
    val emailError = if (email.isNotEmpty()) FormValidation.email(email) else null
    val parentChoices = usersWithRole(state, UserRole.PARENT).map { Choice(it.uid, it.label + if (it.active) "" else " (inactive)") }
    val studentLogins = listOf(Choice("", "No learner login")) +
        usersWithRole(state, UserRole.STUDENT).map { Choice(it.uid, it.label) }

    FormDialog(
        title = if (existing == null) "Enroll Student" else "Edit ${existing.fullName}",
        confirmText = if (existing == null) "Enroll" else "Save",
        saving = saving,
        error = error,
        canSubmit = firstName.isNotBlank() && classId.isNotBlank() && emailError == null,
        onDismiss = onDismiss,
        onConfirm = {
            viewModel.saveStudent(
                (existing ?: Student()).copy(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    studentNumber = studentNo.trim(),
                    email = email.trim(),
                    classId = classId,
                    parentIds = parentIds.toList(),
                    userId = userId
                ),
                onDismiss
            )
        }
    ) {
        FormField(firstName, { firstName = it }, "First name", "student_first_name_input")
        FormField(lastName, { lastName = it }, "Last name", "student_last_name_input")
        FormField(studentNo, { studentNo = it }, "Admission / student #", "student_number_input")
        FormField(email, { email = it }, "Learner email (optional)", "student_email_input", emailError)
        DropdownSelector("Class", classChoices(state), classId, { classId = it }, "student_class_select", "Select a class")
        MultiSelectList("Parents / guardians", parentChoices, parentIds, { parentIds = it }, "student_parent",
            "No parent accounts yet. Parents can register, or use \"+ Parent account\".")
        DropdownSelector("Learner's own login", studentLogins, userId, { userId = it }, "student_login_select")
    }
}

@Composable
private fun NewParentDialog(student: Student, viewModel: SchoolAuthViewModel, onDismiss: () -> Unit) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val emailError = if (email.isNotEmpty()) FormValidation.email(email) else null
    val phoneError = FormValidation.phone(phone)
    FormDialog(
        title = "New parent for ${student.fullName}",
        confirmText = "Create & link",
        saving = saving,
        error = error,
        canSubmit = name.isNotBlank() && email.isNotBlank() && emailError == null && phoneError == null,
        onDismiss = onDismiss,
        onConfirm = { viewModel.createParentForStudent(student, name, email, phone, onDismiss) }
    ) {
        Text("Creates an active parent login linked to this learner and emails a password link.", fontSize = 11.sp, color = SchoolSlate)
        FormField(name, { name = it }, "Parent full name", "new_parent_name_input")
        FormField(email, { email = it }, "Parent email", "new_parent_email_input", emailError)
        FormField(phone, { phone = it }, "Phone (optional)", "new_parent_phone_input", phoneError)
    }
}

@Composable
private fun NewLearnerLoginDialog(student: Student, viewModel: SchoolAuthViewModel, onDismiss: () -> Unit) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var email by remember { mutableStateOf(student.email) }
    val emailError = if (email.isNotEmpty()) FormValidation.email(email) else null
    FormDialog(
        title = "Learner login for ${student.fullName}",
        confirmText = "Create login",
        saving = saving,
        error = error,
        canSubmit = email.isNotBlank() && emailError == null,
        onDismiss = onDismiss,
        onConfirm = { viewModel.createStudentLogin(student, email, onDismiss) }
    ) {
        Text("The learner (or parent) receives a link at this address to set the password.", fontSize = 11.sp, color = SchoolSlate)
        FormField(email, { email = it }, "Login email", "new_learner_email_input", emailError)
    }
}

// ---------- Classes ----------

@Composable
private fun AdminClassesTab(state: SchoolState, viewModel: SchoolAuthViewModel) {
    var editing by remember { mutableStateOf<SchoolClass?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SchoolClass?>(null) }
    val teachersById = state.teachers.items.associateBy { it.uid }
    val subjectsById = state.subjects.items.associateBy { it.id }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabHeader("Classes (${state.classes.items.size})", "Add Class", "admin_add_class_button") { viewModel.clearFormError(); creating = true }
        RemoteContent(state.classes, "No classes yet. Tap \"Add Class\".", state.classes.items.sortedBy { it.name }) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { cls ->
                    val learners = state.students.items.count { it.classId == cls.id }
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cls.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${cls.grade.ifBlank { "No grade" }} • $learners learner(s)", fontSize = 11.sp, color = SchoolSlate)
                            }
                            RowActions({ viewModel.clearFormError(); editing = cls }, { deleting = cls }, "class_${cls.id}")
                        }
                        Text("Teachers: ${cls.teacherIds.mapNotNull { teachersById[it]?.name }.joinToString().ifBlank { "none" }}", fontSize = 11.sp, color = Color(0xFF2563EB))
                        Text("Subjects: ${cls.subjectIds.mapNotNull { subjectsById[it]?.name }.joinToString().ifBlank { "none" }}", fontSize = 11.sp, color = Color(0xFF7C3AED))
                    }
                }
            }
        }
    }

    if (creating) ClassDialog(null, state, viewModel) { creating = false }
    editing?.let { c -> ClassDialog(c, state, viewModel) { editing = null } }
    deleting?.let { c -> ConfirmDeleteDialog("class ${c.name}", { viewModel.deleteClass(c) }, { deleting = null }) }
}

@Composable
private fun ClassDialog(existing: SchoolClass?, state: SchoolState, viewModel: SchoolAuthViewModel, onDismiss: () -> Unit) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var grade by remember { mutableStateOf(existing?.grade.orEmpty()) }
    var teacherIds by remember { mutableStateOf(existing?.teacherIds.orEmpty().toSet()) }
    var subjectIds by remember { mutableStateOf(existing?.subjectIds.orEmpty().toSet()) }

    FormDialog(
        title = if (existing == null) "Create Class" else "Edit ${existing.name}",
        confirmText = if (existing == null) "Create" else "Save",
        saving = saving,
        error = error,
        canSubmit = name.isNotBlank() && grade.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = {
            viewModel.saveClass(
                (existing ?: SchoolClass()).copy(name = name.trim(), grade = grade.trim(), teacherIds = teacherIds.toList(), subjectIds = subjectIds.toList()),
                existing,
                onDismiss
            )
        }
    ) {
        FormField(name, { name = it }, "Class name (e.g. Grade 4C)", "class_name_input")
        FormField(grade, { grade = it }, "Grade (e.g. Grade 4)", "class_grade_input")
        MultiSelectList("Class teachers", teacherChoices(state), teacherIds, { teacherIds = it }, "class_teacher", "Add teachers first.")
        MultiSelectList("Subjects", subjectChoices(state), subjectIds, { subjectIds = it }, "class_subject", "Add subjects first.")
    }
}

// ---------- Subjects ----------

@Composable
private fun AdminSubjectsTab(state: SchoolState, viewModel: SchoolAuthViewModel) {
    var editing by remember { mutableStateOf<Subject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Subject?>(null) }
    val teachersById = state.teachers.items.associateBy { it.uid }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabHeader("Subjects (${state.subjects.items.size})", "Add Subject", "admin_add_subject_button") { viewModel.clearFormError(); creating = true }
        RemoteContent(state.subjects, "No subjects yet. Tap \"Add Subject\".", state.subjects.items.sortedBy { it.name }) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { subject ->
                    val classNames = state.classes.items.filter { subject.id in it.subjectIds }.map { it.name }
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(subject.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Code: ${subject.code.ifBlank { "—" }}", fontSize = 11.sp, color = SchoolSlate)
                            }
                            RowActions({ viewModel.clearFormError(); editing = subject }, { deleting = subject }, "subject_${subject.id}")
                        }
                        Text("Teachers: ${subject.teacherIds.mapNotNull { teachersById[it]?.name }.joinToString().ifBlank { "none" }}", fontSize = 11.sp, color = Color(0xFF2563EB))
                        Text("Classes: ${classNames.joinToString().ifBlank { "none" }}", fontSize = 11.sp, color = Color(0xFF7C3AED))
                    }
                }
            }
        }
    }

    if (creating) SubjectDialog(null, state, viewModel) { creating = false }
    editing?.let { s -> SubjectDialog(s, state, viewModel) { editing = null } }
    deleting?.let { s -> ConfirmDeleteDialog("subject ${s.name}", { viewModel.deleteSubject(s) }, { deleting = null }) }
}

@Composable
private fun SubjectDialog(existing: Subject?, state: SchoolState, viewModel: SchoolAuthViewModel, onDismiss: () -> Unit) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var code by remember { mutableStateOf(existing?.code.orEmpty()) }
    var teacherIds by remember { mutableStateOf(existing?.teacherIds.orEmpty().toSet()) }
    var classIds by remember {
        mutableStateOf(existing?.let { s -> state.classes.items.filter { s.id in it.subjectIds }.map { it.id }.toSet() }.orEmpty())
    }

    FormDialog(
        title = if (existing == null) "Add Subject" else "Edit ${existing.name}",
        confirmText = if (existing == null) "Add" else "Save",
        saving = saving,
        error = error,
        canSubmit = name.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = {
            viewModel.saveSubject(
                (existing ?: Subject()).copy(name = name.trim(), code = code.trim(), teacherIds = teacherIds.toList()),
                existing,
                classIds.toList(),
                onDismiss
            )
        }
    ) {
        FormField(name, { name = it }, "Subject name", "subject_name_input")
        FormField(code, { code = it }, "CAPS code", "subject_code_input")
        MultiSelectList("Teachers", teacherChoices(state), teacherIds, { teacherIds = it }, "subject_teacher", "Add teachers first.")
        MultiSelectList("Offered in classes", classChoices(state), classIds, { classIds = it }, "subject_class", "Add classes first.")
    }
}

// ---------- Assignments ----------

@Composable
private fun AdminAssignmentsTab(state: SchoolState, viewModel: SchoolAuthViewModel) {
    var editing by remember { mutableStateOf<Assignment?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Assignment?>(null) }
    var classFilter by remember { mutableStateOf("ALL") }
    val classesById = state.classes.items.associateBy { it.id }
    val subjectsById = state.subjects.items.associateBy { it.id }
    val teachersById = state.teachers.items.associateBy { it.uid }
    val filtered = state.assignments.items.filter { classFilter == "ALL" || it.classId == classFilter }
        .sortedByDescending { it.createdAt }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabHeader("Assignments (${state.assignments.items.size})", "New Assignment", "admin_add_assignment_button") { viewModel.clearFormError(); creating = true }
        ChoiceChips(listOf(Choice("ALL", "All Classes")) + state.classes.items.sortedBy { it.name }.map { Choice(it.id, it.name) }, classFilter, { classFilter = it }, "assignment_class_filter")
        RemoteContent(state.assignments, "No assignments yet.", filtered) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { a ->
                    AssignmentCard(a, classesById[a.classId]?.name, subjectsById[a.subjectId]?.name, teachersById[a.teacherId]?.name,
                        onEdit = { viewModel.clearFormError(); editing = a }, onDelete = { deleting = a })
                }
            }
        }
    }

    if (creating) AssignmentDialog(null, state.classes.items, state.subjects.items, state.teachers.items, null, viewModel) { creating = false }
    editing?.let { a -> AssignmentDialog(a, state.classes.items, state.subjects.items, state.teachers.items, null, viewModel) { editing = null } }
    deleting?.let { a -> ConfirmDeleteDialog("assignment \"${a.title}\"", { viewModel.deleteAssignment(a) }, { deleting = null }) }
}

@Composable
fun AssignmentCard(
    assignment: Assignment,
    className: String?,
    subjectName: String?,
    teacherName: String?,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val status = SchoolAccess.dueStatus(assignment.dueDate?.toDate())
    val statusColor = when (status) {
        SchoolAccess.DueStatus.OVERDUE -> Color(0xFFDC2626)
        SchoolAccess.DueStatus.DUE_TODAY -> Color(0xFFB45309)
        SchoolAccess.DueStatus.UPCOMING -> Color(0xFF059669)
        SchoolAccess.DueStatus.NO_DUE_DATE -> SchoolSlate
    }
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(assignment.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DiscoveryGreen, modifier = Modifier.weight(1f).testTag("assignment_title"))
            if (onEdit != null || onDelete != null) RowActions(onEdit, onDelete, "assignment_${assignment.id}")
        }
        if (assignment.description.isNotBlank()) Text(assignment.description, fontSize = 12.sp, color = Color(0xFF334155))
        Text(
            listOfNotNull(className, subjectName, teacherName?.let { "by $it" }).joinToString(" • ").ifBlank { "—" },
            fontSize = 11.sp, color = SchoolSlate
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Due: ${formatDate(assignment.dueDate)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
            Pill(status.label, statusColor)
        }
    }
}

/**
 * Create/edit an assignment. [lockedTeacherId] is set in the teacher portal, where the
 * teacher can only target their own classes (rules enforce the same).
 */
@Composable
fun AssignmentDialog(
    existing: Assignment?,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    teachers: List<Teacher>,
    lockedTeacherId: String?,
    viewModel: SchoolAuthViewModel,
    onDismiss: () -> Unit
) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var classId by remember { mutableStateOf(existing?.classId ?: classes.singleOrNull()?.id.orEmpty()) }
    var subjectId by remember { mutableStateOf(existing?.subjectId.orEmpty()) }
    var teacherId by remember { mutableStateOf(lockedTeacherId ?: existing?.teacherId.orEmpty()) }
    var dueDate by remember { mutableStateOf(existing?.dueDate) }
    val selectedClass = classes.firstOrNull { it.id == classId }
    val subjectOptions = subjects.filter { selectedClass == null || selectedClass.subjectIds.isEmpty() || it.id in selectedClass.subjectIds }

    FormDialog(
        title = if (existing == null) "Create Assignment" else "Edit Assignment",
        confirmText = if (existing == null) "Publish" else "Save",
        saving = saving,
        error = error,
        canSubmit = title.isNotBlank() && classId.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = {
            viewModel.saveAssignment(
                (existing ?: Assignment()).copy(
                    title = title, description = description, classId = classId,
                    subjectId = subjectId, teacherId = teacherId, dueDate = dueDate
                ),
                onDismiss
            )
        }
    ) {
        FormField(title, { title = it }, "Title", "assignment_title_input")
        FormField(description, { description = it }, "Instructions", "assignment_description_input", minLines = 3)
        DropdownSelector("Class", classes.sortedBy { it.name }.map { Choice(it.id, it.name) }, classId, { classId = it }, "assignment_class_select", "Select a class")
        DropdownSelector("Subject", listOf(Choice("", "General")) + subjectOptions.map { Choice(it.id, it.name) }, subjectId, { subjectId = it }, "assignment_subject_select")
        if (lockedTeacherId == null) {
            DropdownSelector("Teacher", listOf(Choice("", "School administration")) + teachers.sortedBy { it.name }.map { Choice(it.uid, it.name) },
                teacherId, { teacherId = it }, "assignment_teacher_select")
        }
        DueDateField(dueDate, { dueDate = it }, "assignment_due_date")
    }
}

// ---------- Notices ----------

@Composable
private fun AdminNoticesTab(state: SchoolState, viewModel: SchoolAuthViewModel) {
    var editing by remember { mutableStateOf<Notice?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Notice?>(null) }
    val classesById = state.classes.items.associateBy { it.id }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TabHeader("Notices (${state.notices.items.size})", "New Notice", "admin_add_notice_button") { viewModel.clearFormError(); creating = true }
        RemoteContent(state.notices, "No notices yet.", state.notices.items.sortedByDescending { it.createdAt }) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { notice ->
                    NoticeCard(notice, classesById[notice.classId]?.name, showAudience = true,
                        onEdit = { viewModel.clearFormError(); editing = notice }, onDelete = { deleting = notice })
                }
            }
        }
    }

    if (creating) NoticeDialog(null, state.classes.items, NoticeAudience.adminChoices, allowWholeSchool = true, viewModel = viewModel) { creating = false }
    editing?.let { n -> NoticeDialog(n, state.classes.items, NoticeAudience.adminChoices, allowWholeSchool = true, viewModel = viewModel) { editing = null } }
    deleting?.let { n -> ConfirmDeleteDialog("notice \"${n.title}\"", { viewModel.deleteNotice(n) }, { deleting = null }) }
}

@Composable
fun NoticeCard(
    notice: Notice,
    className: String?,
    showAudience: Boolean,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(notice.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f).testTag("notice_title"))
            if (onEdit != null || onDelete != null) RowActions(onEdit, onDelete, "notice_${notice.id}")
        }
        Text(notice.message, fontSize = 12.sp, color = Color(0xFF334155))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showAudience) Pill(if (notice.targetRole == NoticeAudience.ALL) "EVERYONE" else notice.targetRole, Color(0xFF2563EB))
            if (className != null) Pill(className, Color(0xFFB45309))
            if (!notice.published) Pill("DRAFT", SchoolSlate)
            Text(formatDateTime(notice.createdAt), fontSize = 10.sp, color = SchoolSlate)
        }
    }
}

@Composable
fun NoticeDialog(
    existing: Notice?,
    classes: List<SchoolClass>,
    audiences: List<String>,
    allowWholeSchool: Boolean,
    viewModel: SchoolAuthViewModel,
    onDismiss: () -> Unit
) {
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.formError.collectAsState()
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var message by remember { mutableStateOf(existing?.message.orEmpty()) }
    var audience by remember { mutableStateOf(existing?.targetRole ?: audiences.first()) }
    var classId by remember { mutableStateOf(existing?.classId ?: if (allowWholeSchool) "" else classes.firstOrNull()?.id.orEmpty()) }
    var published by remember { mutableStateOf(existing?.published ?: true) }
    val classOptions = (if (allowWholeSchool) listOf(Choice("", "Whole school")) else emptyList()) +
        classes.sortedBy { it.name }.map { Choice(it.id, it.name) }

    FormDialog(
        title = if (existing == null) "New Notice" else "Edit Notice",
        confirmText = if (published) "Publish" else "Save draft",
        saving = saving,
        error = error,
        canSubmit = title.isNotBlank() && message.isNotBlank() && (allowWholeSchool || classId.isNotBlank()),
        onDismiss = onDismiss,
        onConfirm = {
            viewModel.saveNotice(
                (existing ?: Notice()).copy(title = title, message = message, targetRole = audience, classId = classId, published = published),
                onDismiss
            )
        }
    ) {
        FormField(title, { title = it }, "Notice title", "notice_title_input")
        FormField(message, { message = it }, "Message", "notice_message_input", minLines = 3)
        DropdownSelector("Audience", audiences.map { Choice(it, if (it == NoticeAudience.ALL) "Everyone" else it.lowercase().replaceFirstChar { c -> c.uppercase() } + "s") },
            audience, { audience = it }, "notice_audience_select")
        DropdownSelector("Class", classOptions, classId, { classId = it }, "notice_class_select", "Select a class")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { published = !published }.testTag("notice_published_toggle")) {
            Checkbox(checked = published, onCheckedChange = { published = it })
            Text("Published (uncheck to keep as a draft)", fontSize = 13.sp)
        }
    }
}

// ---------- Settings ----------

@Composable
private fun AdminSettingsTab(state: SchoolState, viewModel: SchoolAuthViewModel) {
    val saving by viewModel.saving.collectAsState()
    val settings = state.schoolSettings
    // Keyed on the loaded document so fields refresh when Firestore delivers it.
    var schoolName by remember(settings) { mutableStateOf(settings.schoolName) }
    var emisNumber by remember(settings) { mutableStateOf(settings.emisNumber) }
    var district by remember(settings) { mutableStateOf(settings.district) }
    var province by remember(settings) { mutableStateOf(settings.province) }
    var principal by remember(settings) { mutableStateOf(settings.principalName) }
    var email by remember(settings) { mutableStateOf(settings.contactEmail) }
    var phone by remember(settings) { mutableStateOf(settings.contactPhone) }
    var term by remember(settings) { mutableStateOf(settings.academicTerm) }
    val emailError = if (email.isNotBlank()) FormValidation.email(email) else null
    val phoneError = FormValidation.phone(phone)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("School Configuration & Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)
        state.settings.error?.let { ErrorState(it) }
        if (state.settings.loading) com.example.ui.components.LoadingState()
        InfoCard {
            FormField(schoolName, { schoolName = it }, "School Name", "settings_name_input", if (schoolName.isBlank()) "School name is required." else null)
            FormField(emisNumber, { emisNumber = it }, "EMIS Number (GDE)", "settings_emis_input")
            FormField(district, { district = it }, "Education District", "settings_district_input")
            FormField(province, { province = it }, "Province", "settings_province_input")
            FormField(principal, { principal = it }, "Principal Name", "settings_principal_input")
            FormField(email, { email = it }, "Administrative Email", "settings_email_input", emailError)
            FormField(phone, { phone = it }, "Contact Telephone", "settings_phone_input", phoneError)
            FormField(term, { term = it }, "Academic Term", "settings_term_input")
            Button(
                onClick = {
                    viewModel.saveSettings(
                        SchoolSettings(
                            schoolName = schoolName.trim(), emisNumber = emisNumber.trim(), district = district.trim(),
                            province = province.trim(), principalName = principal.trim(), contactEmail = email.trim(),
                            contactPhone = phone.trim(), academicTerm = term.trim()
                        )
                    )
                },
                enabled = !saving && schoolName.isNotBlank() && emailError == null && phoneError == null,
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("admin_save_settings_button")
            ) {
                Text(if (saving) "Saving…" else "Save Settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}
