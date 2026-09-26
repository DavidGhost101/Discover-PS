package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Assignment
import com.example.data.model.Notice
import com.example.data.model.NoticeAudience
import com.example.data.model.SchoolClass
import com.example.data.model.Teacher
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.Remote
import com.example.domain.SchoolAccess
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.InfoCard
import com.example.ui.components.LoadingState
import com.example.ui.components.Pill
import com.example.ui.components.PortalHeader
import com.example.ui.components.PortalTabs
import com.example.ui.components.ProfileCard
import com.example.ui.components.RemoteContent
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusBanner
import com.example.ui.theme.DiscoveryGold
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.example.ui.viewmodel.SchoolState
import com.example.ui.viewmodel.TeacherTab

@Composable
fun TeacherDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.teacherTab.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val state by viewModel.school.collectAsState()

    val profile = state.teacherProfile.items.firstOrNull()
    val myClasses = SchoolAccess.teacherClasses(profile, state.classes.items)
    val classIds = myClasses.map { it.id }.toSet()

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        PortalHeader(
            title = "Teacher Portal",
            badge = "TEACHER",
            badgeColor = DiscoveryGreen,
            subtitle = listOf(user.label, myClasses.joinToString { it.name }).filter { it.isNotBlank() }.joinToString(" • "),
            icon = Icons.Default.School,
            onSignOut = { viewModel.signOut() }
        )
        StatusBanner(statusMsg) { viewModel.clearStatusMessage() }
        PortalTabs(TeacherTab.entries, currentTab, { it.title }) { viewModel.setTeacherTab(it) }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (currentTab == TeacherTab.PROFILE) {
                val saving by viewModel.saving.collectAsState()
                val error by viewModel.formError.collectAsState()
                ProfileCard(
                    name = user.displayName,
                    email = user.email,
                    phone = user.phoneNumber,
                    roleLabel = "TEACHER",
                    accent = DiscoveryGreen,
                    saving = saving,
                    error = error,
                    details = listOf(
                        "Employee number" to profile?.employeeNumber.orEmpty(),
                        "Classes" to myClasses.joinToString { it.name },
                        "Subjects" to state.subjects.items.filter { it.id in profile?.subjectIds.orEmpty() }.joinToString { it.name }
                    ),
                    onSave = { name, phone, done -> viewModel.updateOwnProfile(name, phone, done) },
                    onResetPassword = { viewModel.sendMyPasswordReset() },
                    onSignOut = { viewModel.signOut() }
                )
            } else {
                TeacherProfileGate(state.teacherProfile) {
                    when (currentTab) {
                        TeacherTab.MY_CLASSES -> TeacherClassesTab(state, profile!!, myClasses)
                        TeacherTab.MY_STUDENTS -> TeacherStudentsTab(state, myClasses)
                        TeacherTab.ASSIGNMENTS -> TeacherAssignmentsTab(state, user, myClasses, classIds, viewModel)
                        TeacherTab.NOTICES -> TeacherNoticesTab(state, user, myClasses, classIds, viewModel)
                        TeacherTab.PROFILE -> Unit
                    }
                }
            }
        }
    }
}

/** Shows why nothing can be displayed when the admin has not created a teacher record yet. */
@Composable
private fun TeacherProfileGate(profile: Remote<Teacher>, content: @Composable () -> Unit) {
    when {
        profile.error != null -> Column(Modifier.padding(16.dp)) { ErrorState(profile.error) }
        profile.loading -> LoadingState()
        profile.items.isEmpty() -> EmptyState(
            "Your login is not linked to a teacher record yet. Ask the school administrator to add you under Teachers.",
            Modifier.padding(16.dp)
        )
        else -> content()
    }
}

@Composable
private fun TeacherClassesTab(state: SchoolState, teacher: Teacher, myClasses: List<SchoolClass>) {
    val subjectsById = state.subjects.items.associateBy { it.id }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("My Assigned Classes (${myClasses.size})")
        state.students.error?.let { ErrorState(it) }
        if (myClasses.isEmpty()) {
            EmptyState("No classes are assigned to you yet. The administrator assigns classes under Teachers or Classes.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(myClasses, key = { it.id }) { cls ->
                    val classStudents = state.students.items.filter { it.classId == cls.id }.sortedBy { it.fullName }
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cls.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.testTag("teacher_class_name"))
                                Text(cls.grade, fontSize = 11.sp, color = SchoolSlate)
                            }
                            Pill("${classStudents.size} learners", Color(0xFFB45309))
                        }
                        val subjectNames = cls.subjectIds.mapNotNull { subjectsById[it]?.name }
                        Text("Subjects: ${subjectNames.joinToString().ifBlank { "none" }}", fontSize = 11.sp, color = Color(0xFF7C3AED))
                        val mySubjects = teacher.subjectIds.mapNotNull { subjectsById[it]?.name }
                        if (mySubjects.isNotEmpty()) Text("You teach: ${mySubjects.joinToString()}", fontSize = 11.sp, color = DiscoveryGreen)
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        if (classStudents.isEmpty()) Text("No learners enrolled.", fontSize = 11.sp, color = SchoolSlate)
                        classStudents.forEach { student ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• ${student.fullName}", fontSize = 12.sp, color = Color(0xFF334155))
                                Text(student.studentNumber, fontSize = 11.sp, color = SchoolSlate)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherStudentsTab(state: SchoolState, myClasses: List<SchoolClass>) {
    var search by remember { mutableStateOf("") }
    val classesById = myClasses.associateBy { it.id }
    val filtered = state.students.items.filter {
        search.isBlank() || it.fullName.contains(search, ignoreCase = true) || it.studentNumber.contains(search, ignoreCase = true)
    }.sortedBy { it.fullName }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Learners in My Classes (${state.students.items.size})")
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search by learner name or admission #…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SchoolSlate) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("teacher_student_search"),
            shape = RoundedCornerShape(10.dp)
        )
        RemoteContent(state.students, if (search.isBlank()) "No learners in your classes yet." else "No learners match.", filtered) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.uid }) { student ->
                    InfoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(student.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.testTag("teacher_student_name"))
                                Text("No: ${student.studentNumber.ifBlank { "—" }}", fontSize = 11.sp, color = SchoolSlate)
                            }
                            Pill(classesById[student.classId]?.name ?: "", DiscoveryGreen)
                        }
                        if (student.phoneNumber.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp), tint = SchoolSlate)
                                Text(student.phoneNumber, fontSize = 11.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherAssignmentsTab(
    state: SchoolState,
    user: User,
    myClasses: List<SchoolClass>,
    classIds: Set<String>,
    viewModel: SchoolAuthViewModel
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Assignment?>(null) }
    var deleting by remember { mutableStateOf<Assignment?>(null) }
    val assignments = SchoolAccess.assignmentsForClasses(state.assignments.items, classIds)
    val classesById = state.classes.items.associateBy { it.id }
    val subjectsById = state.subjects.items.associateBy { it.id }
    val teachersById = state.teachers.items.associateBy { it.uid }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Class Assignments (${assignments.size})", Modifier.weight(1f))
            Button(
                onClick = { viewModel.clearFormError(); creating = true },
                enabled = myClasses.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("teacher_create_assignment_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Task", fontSize = 12.sp)
            }
        }
        if (myClasses.isEmpty()) Text("You need an assigned class before you can publish assignments.", fontSize = 11.sp, color = SchoolSlate)
        RemoteContent(state.assignments, "No assignments for your classes yet.", assignments) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { a ->
                    val mine = a.teacherId == user.uid
                    AssignmentCard(
                        a, classesById[a.classId]?.name, subjectsById[a.subjectId]?.name, teachersById[a.teacherId]?.name,
                        onEdit = if (mine) ({ viewModel.clearFormError(); editing = a }) else null,
                        onDelete = if (mine) ({ deleting = a }) else null
                    )
                }
            }
        }
    }

    val subjects = state.subjects.items
    if (creating) AssignmentDialog(null, myClasses, subjects, emptyList(), user.uid, viewModel) { creating = false }
    editing?.let { a -> AssignmentDialog(a, myClasses, subjects, emptyList(), user.uid, viewModel) { editing = null } }
    deleting?.let { a -> ConfirmDeleteDialog("assignment \"${a.title}\"", { viewModel.deleteAssignment(a) }, { deleting = null }) }
}

@Composable
private fun TeacherNoticesTab(
    state: SchoolState,
    user: User,
    myClasses: List<SchoolClass>,
    classIds: Set<String>,
    viewModel: SchoolAuthViewModel
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Notice?>(null) }
    var deleting by remember { mutableStateOf<Notice?>(null) }
    val notices = SchoolAccess.visibleNotices(state.notices.items, UserRole.TEACHER, user.uid, classIds)
    val classesById = state.classes.items.associateBy { it.id }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Notices (${notices.size})", Modifier.weight(1f))
            Button(
                onClick = { viewModel.clearFormError(); creating = true },
                enabled = myClasses.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGold),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("teacher_publish_notice_button")
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Class Notice", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        RemoteContent(state.notices, "No notices for teachers yet.", notices) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { notice ->
                    val mine = notice.authorId == user.uid
                    NoticeCard(
                        notice, classesById[notice.classId]?.name, showAudience = true,
                        onEdit = if (mine) ({ viewModel.clearFormError(); editing = notice }) else null,
                        onDelete = if (mine) ({ deleting = notice }) else null
                    )
                }
            }
        }
    }

    if (creating) NoticeDialog(null, myClasses, NoticeAudience.teacherChoices, allowWholeSchool = false, viewModel = viewModel) { creating = false }
    editing?.let { n -> NoticeDialog(n, myClasses, NoticeAudience.teacherChoices, allowWholeSchool = false, viewModel = viewModel) { editing = null } }
    deleting?.let { n -> ConfirmDeleteDialog("notice \"${n.title}\"", { viewModel.deleteNotice(n) }, { deleting = null }) }
}
