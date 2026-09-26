package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Assignment
import com.example.data.model.Notice
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolSettings
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.Teacher
import com.example.data.model.User
import com.example.ui.theme.DiscoveryGold
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.DiscoveryGreenDark
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.AdminTab
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.google.firebase.Timestamp

@Composable
fun AdminDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.adminTab.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()

    val teachers by viewModel.teachers.collectAsState()
    val students by viewModel.students.collectAsState()
    val classes by viewModel.classes.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val notices by viewModel.notices.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Dialog state holders
    var showAddTeacherDialog by remember { mutableStateOf(false) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddAssignmentDialog by remember { mutableStateOf(false) }
    var showAddNoticeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Admin Header
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DiscoveryGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = DiscoveryGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Admin Console",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .background(DiscoveryGold, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ADMIN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        }
                        Text(
                            text = user.displayName.ifBlank { "Principal Raymond Peters" },
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.testTag("admin_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Sign Out",
                        tint = Color.White
                    )
                }
            }
        }

        // Status Message Notification
        if (statusMsg != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F5E9))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusMsg ?: "",
                    color = Color(0xFF2E7D32),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = { viewModel.clearStatusMessage() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                }
            }
        }

        // Navigation Tabs (8 focused tabs per requirements)
        ScrollableTabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = Color.White,
            contentColor = DiscoveryGreen,
            edgePadding = 8.dp
        ) {
            AdminTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { viewModel.setAdminTab(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}")
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (currentTab) {
                AdminTab.DASHBOARD -> AdminOverviewTab(
                    teachersCount = teachers.size,
                    studentsCount = students.size,
                    classesCount = classes.size,
                    subjectsCount = subjects.size,
                    assignmentsCount = assignments.size,
                    noticesCount = notices.size,
                    recentNotices = notices.take(3),
                    onNavigateTab = { viewModel.setAdminTab(it) },
                    onAddTeacher = { showAddTeacherDialog = true },
                    onAddStudent = { showAddStudentDialog = true },
                    onAddClass = { showAddClassDialog = true },
                    onAddNotice = { showAddNoticeDialog = true }
                )
                AdminTab.TEACHERS -> AdminTeachersTab(
                    teachers = teachers,
                    classes = classes,
                    subjects = subjects,
                    onAddClick = { showAddTeacherDialog = true },
                    onDeleteClick = { viewModel.deleteTeacher(it) }
                )
                AdminTab.STUDENTS -> AdminStudentsTab(
                    students = students,
                    classes = classes,
                    onAddClick = { showAddStudentDialog = true },
                    onDeleteClick = { viewModel.deleteStudent(it) }
                )
                AdminTab.CLASSES -> AdminClassesTab(
                    classes = classes,
                    teachers = teachers,
                    students = students,
                    onAddClick = { showAddClassDialog = true },
                    onDeleteClick = { viewModel.deleteClass(it) }
                )
                AdminTab.SUBJECTS -> AdminSubjectsTab(
                    subjects = subjects,
                    onAddClick = { showAddSubjectDialog = true },
                    onDeleteClick = { viewModel.deleteSubject(it) }
                )
                AdminTab.ASSIGNMENTS -> AdminAssignmentsTab(
                    assignments = assignments,
                    classes = classes,
                    subjects = subjects,
                    onAddClick = { showAddAssignmentDialog = true },
                    onDeleteClick = { viewModel.deleteAssignment(it) }
                )
                AdminTab.NOTICES -> AdminNoticesTab(
                    notices = notices,
                    classes = classes,
                    onAddClick = { showAddNoticeDialog = true },
                    onDeleteClick = { viewModel.deleteNotice(it) }
                )
                AdminTab.SETTINGS -> AdminSettingsTab(
                    settings = settings,
                    onSaveSettings = { viewModel.saveSettings(it) }
                )
            }
        }
    }

    // Dialogs
    if (showAddTeacherDialog) {
        AddTeacherDialog(
            classes = classes,
            subjects = subjects,
            onDismiss = { showAddTeacherDialog = false },
            onSave = { teacher ->
                viewModel.saveTeacher(teacher)
                showAddTeacherDialog = false
            }
        )
    }

    if (showAddStudentDialog) {
        AddStudentDialog(
            classes = classes,
            onDismiss = { showAddStudentDialog = false },
            onSave = { student ->
                viewModel.saveStudent(student)
                showAddStudentDialog = false
            }
        )
    }

    if (showAddClassDialog) {
        AddClassDialog(
            teachers = teachers,
            onDismiss = { showAddClassDialog = false },
            onSave = { cls ->
                viewModel.saveClass(cls)
                showAddClassDialog = false
            }
        )
    }

    if (showAddSubjectDialog) {
        AddSubjectDialog(
            onDismiss = { showAddSubjectDialog = false },
            onSave = { subj ->
                viewModel.saveSubject(subj)
                showAddSubjectDialog = false
            }
        )
    }

    if (showAddAssignmentDialog) {
        AddAssignmentDialog(
            classes = classes,
            subjects = subjects,
            teachers = teachers,
            onDismiss = { showAddAssignmentDialog = false },
            onSave = { ass ->
                viewModel.saveAssignment(ass)
                showAddAssignmentDialog = false
            }
        )
    }

    if (showAddNoticeDialog) {
        AddNoticeDialog(
            classes = classes,
            authorName = user.displayName.ifBlank { "Administration" },
            onDismiss = { showAddNoticeDialog = false },
            onSave = { not ->
                viewModel.saveNotice(not)
                showAddNoticeDialog = false
            }
        )
    }
}

// Overview Tab
@Composable
fun AdminOverviewTab(
    teachersCount: Int,
    studentsCount: Int,
    classesCount: Int,
    subjectsCount: Int,
    assignmentsCount: Int,
    noticesCount: Int,
    recentNotices: List<Notice>,
    onNavigateTab: (AdminTab) -> Unit,
    onAddTeacher: () -> Unit,
    onAddStudent: () -> Unit,
    onAddClass: () -> Unit,
    onAddNotice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "School Operations Snapshot",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DiscoveryGreen
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard(title = "Teachers", count = teachersCount.toString(), icon = Icons.Default.School, color = DiscoveryGreen, modifier = Modifier.weight(1f), onClick = { onNavigateTab(AdminTab.TEACHERS) })
            StatMetricCard(title = "Students", count = studentsCount.toString(), icon = Icons.Default.People, color = DiscoveryGold, modifier = Modifier.weight(1f), onClick = { onNavigateTab(AdminTab.STUDENTS) })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard(title = "Classes", count = classesCount.toString(), icon = Icons.Default.Class, color = Color(0xFF2563EB), modifier = Modifier.weight(1f), onClick = { onNavigateTab(AdminTab.CLASSES) })
            StatMetricCard(title = "Subjects", count = subjectsCount.toString(), icon = Icons.Default.Book, color = Color(0xFF7C3AED), modifier = Modifier.weight(1f), onClick = { onNavigateTab(AdminTab.SUBJECTS) })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMetricCard(title = "Assignments", count = assignmentsCount.toString(), icon = Icons.Default.Assignment, color = Color(0xFF059669), modifier = Modifier.weight(1f), onClick = { onNavigateTab(AdminTab.ASSIGNMENTS) })
            StatMetricCard(title = "Notices", count = noticesCount.toString(), icon = Icons.Default.Campaign, color = Color(0xFFDC2626), modifier = Modifier.weight(1f), onClick = { onNavigateTab(AdminTab.NOTICES) })
        }

        // Quick Administrative Actions
        Text("Quick Record Creation", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DiscoveryGreen)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAddTeacher, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Teacher", fontSize = 11.sp)
            }
            Button(onClick = onAddStudent, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Student", fontSize = 11.sp)
            }
            Button(onClick = onAddClass, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Class", fontSize = 11.sp)
            }
            Button(onClick = onAddNotice, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGold), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Notice", fontSize = 11.sp, color = Color.Black)
            }
        }

        // Recent Notices Card
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Latest School Notices", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    TextButton(onClick = { onNavigateTab(AdminTab.NOTICES) }) {
                        Text("View All", fontSize = 12.sp, color = DiscoveryGreen)
                    }
                }
                recentNotices.forEach { not ->
                    Column {
                        Text(not.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E293B))
                        Text(not.message, fontSize = 11.sp, color = SchoolSlate, maxLines = 2)
                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }
    }
}

@Composable
fun StatMetricCard(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Text(text = title, fontSize = 11.sp, color = SchoolSlate)
            }
        }
    }
}

// Teachers Tab
@Composable
fun AdminTeachersTab(
    teachers: List<Teacher>,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = teachers.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.email.contains(searchQuery, ignoreCase = true) ||
                it.employeeNumber.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Teachers Directory (${teachers.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("admin_add_teacher_button")) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Teacher", fontSize = 12.sp)
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name, employee # or email...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SchoolSlate) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered) { teacher ->
                val assignedClassNames = classes.filter { teacher.classIds.contains(it.id) }.map { it.name }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(DiscoveryGreen.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = DiscoveryGreen, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Emp #: ${teacher.employeeNumber} • ${teacher.email}", fontSize = 11.sp, color = SchoolSlate)
                                }
                            }
                            IconButton(onClick = { onDeleteClick(teacher.uid) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Classes: ${if (assignedClassNames.isEmpty()) "Grade 4A" else assignedClassNames.joinToString(", ")}",
                            fontSize = 11.sp,
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Students Tab
@Composable
fun AdminStudentsTab(
    students: List<Student>,
    classes: List<SchoolClass>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedClassId by remember { mutableStateOf("ALL") }

    val filtered = students.filter { student ->
        val matchesClass = selectedClassId == "ALL" || student.classId == selectedClassId
        val matchesSearch = student.fullName.contains(searchQuery, ignoreCase = true) ||
                student.studentNumber.contains(searchQuery, ignoreCase = true)
        matchesClass && matchesSearch
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Enrolled Students (${students.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("admin_add_student_button")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enroll Student", fontSize = 12.sp)
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selectedClassId == "ALL") DiscoveryGreen else Color.White)
                        .clickable { selectedClassId = "ALL" }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("All Classes", fontSize = 11.sp, color = if (selectedClassId == "ALL") Color.White else SchoolSlate, fontWeight = FontWeight.Medium)
                }
            }
            items(classes) { cls ->
                val isSelected = selectedClassId == cls.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) DiscoveryGreen else Color.White)
                        .clickable { selectedClassId = cls.id }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(cls.name, fontSize = 11.sp, color = if (isSelected) Color.White else SchoolSlate, fontWeight = FontWeight.Medium)
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search student name or admission #...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SchoolSlate) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { student ->
                val className = classes.find { it.id == student.classId }?.name ?: "Grade 4"
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(student.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Box(modifier = Modifier.background(DiscoveryGold.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(className, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                }
                            }
                            Text("No: ${student.studentNumber} • Email: ${student.email}", fontSize = 11.sp, color = SchoolSlate)
                        }
                        IconButton(onClick = { onDeleteClick(student.uid) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

// Classes Tab
@Composable
fun AdminClassesTab(
    classes: List<SchoolClass>,
    teachers: List<Teacher>,
    students: List<Student>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("School Classes (${classes.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("admin_add_class_button")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Class", fontSize = 12.sp)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(classes) { cls ->
                val studentCount = students.count { it.classId == cls.id }
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(DiscoveryGreen.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Text(cls.name.take(2), fontWeight = FontWeight.Bold, color = DiscoveryGreen, fontSize = 14.sp)
                            }
                            Column {
                                Text(cls.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Grade: ${cls.grade} • $studentCount Learners", fontSize = 11.sp, color = SchoolSlate)
                            }
                        }
                        IconButton(onClick = { onDeleteClick(cls.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

// Subjects Tab
@Composable
fun AdminSubjectsTab(
    subjects: List<Subject>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("CAPS Subjects (${subjects.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("admin_add_subject_button")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Subject", fontSize = 12.sp)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(subjects) { subj ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(subj.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Code: ${subj.code}", fontSize = 11.sp, color = SchoolSlate)
                        }
                        IconButton(onClick = { onDeleteClick(subj.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

// Assignments Tab
@Composable
fun AdminAssignmentsTab(
    assignments: List<Assignment>,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("School Assignments (${assignments.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("admin_add_assignment_button")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Assignment", fontSize = 12.sp)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(assignments) { ass ->
                val className = classes.find { it.id == ass.classId }?.name ?: "Grade 4"
                val subjName = subjects.find { it.id == ass.subjectId }?.name ?: "Academic"
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(ass.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DiscoveryGreen)
                            IconButton(onClick = { onDeleteClick(ass.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                            }
                        }
                        Text(ass.description, fontSize = 11.sp, color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$className • $subjName", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = SchoolSlate)
                    }
                }
            }
        }
    }
}

// Notices Tab
@Composable
fun AdminNoticesTab(
    notices: List<Notice>,
    classes: List<SchoolClass>,
    onAddClick: () -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("School Notices (${notices.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGold), shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("admin_add_notice_button")) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Publish Notice", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(notices) { notice ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(notice.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { onDeleteClick(notice.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(notice.message, fontSize = 11.sp, color = SchoolSlate)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Target: ${notice.targetRole}", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

// Settings Tab
@Composable
fun AdminSettingsTab(
    settings: SchoolSettings,
    onSaveSettings: (SchoolSettings) -> Unit
) {
    var schoolName by remember { mutableStateOf(settings.schoolName) }
    var emisNumber by remember { mutableStateOf(settings.emisNumber) }
    var district by remember { mutableStateOf(settings.district) }
    var province by remember { mutableStateOf(settings.province) }
    var principal by remember { mutableStateOf(settings.principalName) }
    var email by remember { mutableStateOf(settings.contactEmail) }
    var phone by remember { mutableStateOf(settings.contactPhone) }
    var term by remember { mutableStateOf(settings.academicTerm) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("School Configuration & Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = schoolName, onValueChange = { schoolName = it }, label = { Text("School Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = emisNumber, onValueChange = { emisNumber = it }, label = { Text("EMIS Number (GDE)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("Education District") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = province, onValueChange = { province = it }, label = { Text("Province") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = principal, onValueChange = { principal = it }, label = { Text("Principal Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Administrative Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Telephone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = term, onValueChange = { term = it }, label = { Text("Academic Term") }, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        onSaveSettings(
                            SchoolSettings(
                                schoolName = schoolName,
                                emisNumber = emisNumber,
                                district = district,
                                province = province,
                                principalName = principal,
                                contactEmail = email,
                                contactPhone = phone,
                                academicTerm = term
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("admin_save_settings_button")
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Dialogs
@Composable
fun AddTeacherDialog(
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (Teacher) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var empNo by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Teacher", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = empNo, onValueChange = { empNo = it }, label = { Text("Employee Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (firstName.isNotBlank() && email.isNotBlank()) {
                        onSave(
                            Teacher(
                                firstName = firstName,
                                lastName = lastName,
                                email = email,
                                employeeNumber = empNo.ifBlank { "DPS-EMP-109" },
                                phoneNumber = phone.ifBlank { "+27820000000" },
                                classIds = listOf(classes.firstOrNull()?.id ?: "class_4a")
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddStudentDialog(
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var studentNo by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val selectedClass = classes.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enroll Student", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = studentNo, onValueChange = { studentNo = it }, label = { Text("Admission/Student #") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Student Email") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (firstName.isNotBlank()) {
                        onSave(
                            Student(
                                firstName = firstName,
                                lastName = lastName,
                                studentNumber = studentNo.ifBlank { "DPS-4099" },
                                email = email.ifBlank { "student@discoveryprimary.co.za" },
                                classId = selectedClass?.id ?: "class_4a"
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)
            ) {
                Text("Enroll")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddClassDialog(
    teachers: List<Teacher>,
    onDismiss: () -> Unit,
    onSave: (SchoolClass) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("Grade 4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Class", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Class Name (e.g. Grade 4C)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(SchoolClass(name = name, grade = grade))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)
            ) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onSave: (Subject) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Subject", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Subject Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("CAPS Code") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(Subject(name = name, code = code.ifBlank { "CAPS-SUBJ" }))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)
            ) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddAssignmentDialog(
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    teachers: List<Teacher>,
    onDismiss: () -> Unit,
    onSave: (Assignment) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val cls = classes.firstOrNull()
    val subj = subjects.firstOrNull()
    val teacher = teachers.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Assignment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            Assignment(
                                title = title,
                                description = description,
                                classId = cls?.id ?: "class_4a",
                                subjectId = subj?.id ?: "subj_math",
                                teacherId = teacher?.uid ?: "teach_sithole",
                                dueDate = Timestamp.now()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddNoticeDialog(
    classes: List<SchoolClass>,
    authorName: String,
    onDismiss: () -> Unit,
    onSave: (Notice) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish Notice", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Notice Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && message.isNotBlank()) {
                        onSave(
                            Notice(
                                title = title,
                                message = message,
                                targetRole = "ALL",
                                published = true
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)
            ) {
                Text("Publish")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
