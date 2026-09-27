package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Student
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.domain.SchoolAccess
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.InfoCard
import com.example.ui.components.LoadingState
import com.example.ui.components.PortalHeader
import com.example.ui.components.PortalTabs
import com.example.ui.components.ProfileCard
import com.example.ui.components.RemoteContent
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusBanner
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.example.ui.viewmodel.SchoolState
import com.example.ui.viewmodel.StudentTab

private val StudentBlue = Color(0xFF2563EB)

@Composable
fun StudentDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.studentTab.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val state by viewModel.school.collectAsState()

    // Only the learner's own record is readable (rules: students.userId == auth uid).
    val record = state.students.items.firstOrNull { it.userId == user.uid }
    val cls = state.classes.items.firstOrNull { it.id == record?.classId }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        PortalHeader(
            title = "Student Portal",
            badge = "STUDENT",
            badgeColor = StudentBlue,
            subtitle = listOfNotNull(user.label, cls?.name).joinToString(" • "),
            icon = Icons.Default.School,
            onSignOut = { viewModel.signOut() }
        )
        StatusBanner(statusMsg) { viewModel.clearStatusMessage() }
        PortalTabs(StudentTab.entries, currentTab, { it.title }) { viewModel.setStudentTab(it) }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (currentTab == StudentTab.PROFILE) {
                val saving by viewModel.saving.collectAsState()
                val error by viewModel.formError.collectAsState()
                ProfileCard(
                    name = user.displayName,
                    email = user.email,
                    phone = user.phoneNumber,
                    roleLabel = "STUDENT",
                    accent = StudentBlue,
                    saving = saving,
                    error = error,
                    details = listOf(
                        "Admission number" to record?.studentNumber.orEmpty(),
                        "Class" to (cls?.name ?: "Not assigned"),
                        "Grade" to cls?.grade.orEmpty()
                    ),
                    onSave = { name, phone, done -> viewModel.updateOwnProfile(name, phone, done) },
                    onResetPassword = { viewModel.sendMyPasswordReset() },
                    onSignOut = { viewModel.signOut() }
                )
            } else {
                StudentRecordGate(state, record) { student ->
                    when (currentTab) {
                        StudentTab.MY_SUBJECTS -> StudentSubjects(state, student)
                        StudentTab.ASSIGNMENTS -> StudentAssignments(state, student)
                        StudentTab.NOTICES -> StudentNotices(state, user, student)
                        StudentTab.PROFILE -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentRecordGate(state: SchoolState, record: Student?, content: @Composable (Student) -> Unit) {
    when {
        state.students.error != null -> Column(Modifier.padding(16.dp)) { ErrorState(state.students.error) }
        state.students.loading -> LoadingState()
        record == null -> EmptyState(
            "Your login is not linked to a learner record yet. Ask the school office to link your account.",
            Modifier.padding(16.dp)
        )
        else -> content(record)
    }
}

@Composable
private fun StudentSubjects(state: SchoolState, student: Student) {
    val cls = state.classes.items.firstOrNull { it.id == student.classId }
    val subjects = state.subjects.items.filter { cls != null && it.id in cls.subjectIds }.sortedBy { it.name }
    val teachersById = state.teachers.items.associateBy { it.uid }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("My Subjects${cls?.let { " — ${it.name}" } ?: ""}")
        RemoteContent(state.subjects, "No subjects are listed for your class yet.", subjects) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { subject ->
                    val classTeachers = subject.teacherIds.mapNotNull { teachersById[it] }.filter { student.classId in it.classIds }
                    InfoCard {
                        Text(subject.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.testTag("student_subject_name"))
                        if (subject.code.isNotBlank()) Text("CAPS Code: ${subject.code}", fontSize = 11.sp, color = SchoolSlate)
                        if (classTeachers.isNotEmpty()) Text("Teacher: ${classTeachers.joinToString { it.name }}", fontSize = 11.sp, color = StudentBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentAssignments(state: SchoolState, student: Student) {
    val assignments = SchoolAccess.assignmentsForClasses(state.assignments.items, setOf(student.classId))
    val classesById = state.classes.items.associateBy { it.id }
    val subjectsById = state.subjects.items.associateBy { it.id }
    val teachersById = state.teachers.items.associateBy { it.uid }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("My Homework & Class Tasks")
        RemoteContent(state.assignments, "No assignments yet. Enjoy the break!", assignments) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { a ->
                    AssignmentCard(a, classesById[a.classId]?.name, subjectsById[a.subjectId]?.name, teachersById[a.teacherId]?.name)
                }
            }
        }
    }
}

@Composable
private fun StudentNotices(state: SchoolState, user: User, student: Student) {
    val notices = SchoolAccess.visibleNotices(state.notices.items, UserRole.STUDENT, user.uid, setOf(student.classId))
    val classesById = state.classes.items.associateBy { it.id }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("School Announcements")
        RemoteContent(state.notices, "No announcements right now.", notices) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { n -> NoticeCard(n, classesById[n.classId]?.name, showAudience = false) }
            }
        }
    }
}
