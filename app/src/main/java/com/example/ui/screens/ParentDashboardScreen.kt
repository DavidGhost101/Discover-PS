package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.ui.components.Choice
import com.example.ui.components.ChoiceChips
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.InfoCard
import com.example.ui.components.LabeledValue
import com.example.ui.components.LoadingState
import com.example.ui.components.PortalHeader
import com.example.ui.components.PortalTabs
import com.example.ui.components.ProfileCard
import com.example.ui.components.RemoteContent
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusBanner
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.ParentTab
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.example.ui.viewmodel.SchoolState

private val ParentPurple = Color(0xFF7C3AED)

@Composable
fun ParentDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.parentTab.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val state by viewModel.school.collectAsState()

    // The query already returns only this parent's children; filtering again keeps the UI
    // correct even if cached data from another session were ever present.
    val children = SchoolAccess.childrenOf(user.uid, state.students.items)
    var selectedChildId by rememberSaveable { mutableStateOf("") }
    val selectedChild = children.firstOrNull { it.uid == selectedChildId } ?: children.firstOrNull()

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        PortalHeader(
            title = "Parent Portal",
            badge = "PARENT",
            badgeColor = ParentPurple,
            subtitle = "${user.label} • Guardian",
            icon = Icons.Default.FamilyRestroom,
            onSignOut = { viewModel.signOut() }
        )
        StatusBanner(statusMsg) { viewModel.clearStatusMessage() }
        PortalTabs(ParentTab.entries, currentTab, { it.title }) { viewModel.setParentTab(it) }

        if (children.size > 1 && currentTab != ParentTab.PROFILE && currentTab != ParentTab.NOTICES) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                ChoiceChips(children.map { Choice(it.uid, it.fullName) }, selectedChild?.uid.orEmpty(), { selectedChildId = it }, "child_selector")
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (currentTab) {
                ParentTab.LEARNER -> ChildrenGate(state, children) { ChildProfile(state, selectedChild!!) }
                ParentTab.HOMEWORK -> ChildrenGate(state, children) { ChildHomework(state, selectedChild!!) }
                ParentTab.NOTICES -> ParentNotices(state, user, children)
                ParentTab.PROFILE -> {
                    val saving by viewModel.saving.collectAsState()
                    val error by viewModel.formError.collectAsState()
                    ProfileCard(
                        name = user.displayName,
                        email = user.email,
                        phone = user.phoneNumber,
                        roleLabel = "PARENT",
                        accent = ParentPurple,
                        saving = saving,
                        error = error,
                        details = listOf("Linked children" to children.joinToString { it.fullName }.ifBlank { "None yet" }),
                        onSave = { name, phone, done -> viewModel.updateOwnProfile(name, phone, done) },
                        onResetPassword = { viewModel.sendMyPasswordReset() },
                        onSignOut = { viewModel.signOut() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildrenGate(state: SchoolState, children: List<Student>, content: @Composable () -> Unit) {
    when {
        state.students.error != null -> Column(Modifier.padding(16.dp)) { ErrorState(state.students.error) }
        state.students.loading -> LoadingState()
        children.isEmpty() -> EmptyState(
            "No learners are linked to your account yet. The school administrator links parents to their children.",
            Modifier.padding(16.dp)
        )
        else -> content()
    }
}

@Composable
private fun ChildProfile(state: SchoolState, child: Student) {
    val cls = state.classes.items.firstOrNull { it.id == child.classId }
    val educators = SchoolAccess.teachersOfClass(child.classId, state.teachers.items)
    val subjects = state.subjects.items.filter { cls != null && it.id in cls.subjectIds }
    val homework = SchoolAccess.assignmentsForClasses(state.assignments.items, setOf(child.classId))
    val open = homework.count { SchoolAccess.dueStatus(it.dueDate?.toDate()) != SchoolAccess.DueStatus.OVERDUE }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Learner Profile")
        InfoCard {
            Text(child.fullName, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.testTag("child_name"))
            LabeledValue("Admission number", child.studentNumber)
            LabeledValue("Class", cls?.name ?: "Not assigned")
            LabeledValue("Grade", cls?.grade.orEmpty())
        }
        InfoCard {
            Text("Class educators", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (educators.isEmpty()) Text("No teacher assigned yet.", fontSize = 12.sp, color = SchoolSlate)
            educators.forEach { t ->
                Text(t.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("child_teacher"))
                val contact = listOf(t.email, t.phoneNumber).filter { it.isNotBlank() }.joinToString(" • ")
                if (contact.isNotBlank()) Text(contact, fontSize = 11.sp, color = SchoolSlate)
            }
        }
        InfoCard {
            Text("Subjects", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subjects.joinToString { it.name }.ifBlank { "No subjects listed for this class yet." }, fontSize = 12.sp, color = SchoolSlate)
        }
        InfoCard {
            Text("Homework", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${homework.size} assignment(s) for ${cls?.name ?: "this class"}; $open not past due.", fontSize = 12.sp, color = SchoolSlate)
        }
        state.settings.items.firstOrNull()?.let { s ->
            InfoCard {
                Text(s.schoolName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (s.academicTerm.isNotBlank()) LabeledValue("Term", s.academicTerm)
                if (s.contactPhone.isNotBlank()) LabeledValue("School phone", s.contactPhone)
                if (s.contactEmail.isNotBlank()) LabeledValue("School email", s.contactEmail)
            }
        }
    }
}

@Composable
private fun ChildHomework(state: SchoolState, child: Student) {
    val homework = SchoolAccess.assignmentsForClasses(state.assignments.items, setOf(child.classId))
    val classesById = state.classes.items.associateBy { it.id }
    val subjectsById = state.subjects.items.associateBy { it.id }
    val teachersById = state.teachers.items.associateBy { it.uid }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("${child.firstName.ifBlank { child.fullName }}'s Homework & Tasks")
        RemoteContent(state.assignments, "No assignments for ${classesById[child.classId]?.name ?: "this class"} yet.", homework) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { a ->
                    AssignmentCard(a, classesById[a.classId]?.name, subjectsById[a.subjectId]?.name, teachersById[a.teacherId]?.name)
                }
            }
        }
    }
}

@Composable
private fun ParentNotices(state: SchoolState, user: User, children: List<Student>) {
    val classIds = children.map { it.classId }.toSet()
    val notices = SchoolAccess.visibleNotices(state.notices.items, UserRole.PARENT, user.uid, classIds)
    val classesById = state.classes.items.associateBy { it.id }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("School & Class Notices")
        RemoteContent(state.notices, "No notices for parents right now.", notices) { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { n -> NoticeCard(n, classesById[n.classId]?.name, showAudience = false) }
            }
        }
    }
}
