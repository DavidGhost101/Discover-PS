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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import com.example.data.model.Student
import com.example.data.model.Teacher
import com.example.data.model.User
import com.example.ui.theme.DiscoveryGold
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.DiscoveryGreenDark
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.example.ui.viewmodel.TeacherTab
import com.google.firebase.Timestamp

@Composable
fun TeacherDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.teacherTab.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()

    val teacherProfile = viewModel.getTeacherProfile(user)
    val teacherClasses = viewModel.getTeacherClasses(user)
    val teacherStudents = viewModel.getTeacherStudents(user)
    val teacherAssignments = viewModel.getTeacherAssignments(user)
    val teacherNotices = viewModel.getTeacherNotices(user)

    var showCreateAssignmentDialog by remember { mutableStateOf(false) }
    var showCreateClassNoticeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Teacher Header
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
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = DiscoveryGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Teacher Portal",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .background(DiscoveryGreen, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "TEACHER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Text(
                            text = "${teacherProfile.name} • ${teacherClasses.firstOrNull()?.name ?: "Grade 4A"}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.testTag("teacher_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Sign Out",
                        tint = Color.White
                    )
                }
            }
        }

        // Status Notification Banner
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

        // Teacher Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = Color.White,
            contentColor = DiscoveryGreen,
            edgePadding = 8.dp
        ) {
            TeacherTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { viewModel.setTeacherTab(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.testTag("teacher_tab_${tab.name.lowercase()}")
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
                TeacherTab.MY_CLASSES -> TeacherClassesTab(
                    assignedClasses = teacherClasses,
                    students = teacherStudents
                )
                TeacherTab.MY_STUDENTS -> TeacherStudentsTab(
                    students = teacherStudents
                )
                TeacherTab.ASSIGNMENTS -> TeacherAssignmentsTab(
                    assignments = teacherAssignments,
                    assignedClasses = teacherClasses,
                    onCreateClick = { showCreateAssignmentDialog = true },
                    onDeleteClick = { viewModel.deleteAssignment(it) }
                )
                TeacherTab.NOTICES -> TeacherNoticesTab(
                    notices = teacherNotices,
                    assignedClasses = teacherClasses,
                    onCreateNoticeClick = { showCreateClassNoticeDialog = true }
                )
                TeacherTab.PROFILE -> TeacherProfileTab(
                    user = user,
                    teacher = teacherProfile,
                    assignedClasses = teacherClasses,
                    onSignOut = { viewModel.signOut() }
                )
            }
        }
    }

    if (showCreateAssignmentDialog) {
        TeacherCreateAssignmentDialog(
            teacher = teacherProfile,
            assignedClasses = teacherClasses,
            onDismiss = { showCreateAssignmentDialog = false },
            onSave = { assignment ->
                viewModel.saveAssignment(assignment)
                showCreateAssignmentDialog = false
            }
        )
    }

    if (showCreateClassNoticeDialog) {
        TeacherCreateNoticeDialog(
            teacher = teacherProfile,
            assignedClasses = teacherClasses,
            onDismiss = { showCreateClassNoticeDialog = false },
            onSave = { notice ->
                viewModel.saveNotice(notice)
                showCreateClassNoticeDialog = false
            }
        )
    }
}

// My Classes Tab
@Composable
fun TeacherClassesTab(
    assignedClasses: List<SchoolClass>,
    students: List<Student>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("My Assigned Classes (${assignedClasses.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(assignedClasses) { cls ->
                val classStudents = students.filter { it.classId == cls.id }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DiscoveryGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Class, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                                Column {
                                    Text(cls.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                    Text("Grade: ${cls.grade} • Discovery Primary", fontSize = 11.sp, color = SchoolSlate)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(DiscoveryGold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("${classStudents.size} Students", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB45309))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Enrolled Roster Preview:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SchoolSlate)
                        Spacer(modifier = Modifier.height(4.dp))
                        classStudents.take(4).forEach { student ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
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

// My Students Tab
@Composable
fun TeacherStudentsTab(
    students: List<Student>
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = students.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) ||
                it.studentNumber.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Learners in My Classes (${students.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by student name or admission #...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SchoolSlate) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered) { student ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(DiscoveryGreen.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = DiscoveryGreen, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(student.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("No: ${student.studentNumber}", fontSize = 11.sp, color = SchoolSlate)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DiscoveryGreen)
                            }
                        }

                        if (student.phoneNumber.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp), tint = SchoolSlate)
                                Text("Contact: ${student.phoneNumber}", fontSize = 11.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Assignments Tab
@Composable
fun TeacherAssignmentsTab(
    assignments: List<Assignment>,
    assignedClasses: List<SchoolClass>,
    onCreateClick: () -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Class Assignments (${assignments.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)
            Button(
                onClick = onCreateClick,
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(assignments) { ass ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ass.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DiscoveryGreen)
                            IconButton(onClick = { onDeleteClick(ass.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                            }
                        }
                        Text(ass.description, fontSize = 11.sp, color = Color(0xFF334155))
                    }
                }
            }
        }
    }
}

// Notices Tab
@Composable
fun TeacherNoticesTab(
    notices: List<Notice>,
    assignedClasses: List<SchoolClass>,
    onCreateNoticeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("School & Staff Notices (${notices.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)
            Button(
                onClick = onCreateNoticeClick,
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGold),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("teacher_publish_notice_button")
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Post Notice", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notices) { notice ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(notice.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(notice.message, fontSize = 11.sp, color = SchoolSlate)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Target: ${notice.targetRole}", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

// Profile Tab
@Composable
fun TeacherProfileTab(
    user: User,
    teacher: Teacher,
    assignedClasses: List<SchoolClass>,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(DiscoveryGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = teacher.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 24.sp
                    )
                }

                Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E293B))
                Text(teacher.email, fontSize = 12.sp, color = SchoolSlate)

                Box(
                    modifier = Modifier
                        .background(DiscoveryGold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Role: TEACHER (CAPS Educator)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Educator Particulars", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                ProfileInfoRow("Employee Number", teacher.employeeNumber)
                ProfileInfoRow("Contact Phone", teacher.phoneNumber.ifBlank { "+27 82 555 4102" })
                ProfileInfoRow("Assigned Classes", assignedClasses.joinToString(", ") { it.name })
                ProfileInfoRow("Campus Branch", "Discovery Primary School (D12)")
                ProfileInfoRow("Firestore UID", user.uid.take(16) + "...")
            }
        }

        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("teacher_profile_sign_out_button")
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = SchoolSlate)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
    }
}

// Dialogs
@Composable
fun TeacherCreateAssignmentDialog(
    teacher: Teacher,
    assignedClasses: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSave: (Assignment) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val cls = assignedClasses.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Class Assignment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Task Instructions") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Assigned to: ${cls?.name ?: "Grade 4A"}", fontSize = 11.sp, color = DiscoveryGreen, fontWeight = FontWeight.Bold)
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
                                subjectId = "subj_eng",
                                teacherId = teacher.uid,
                                dueDate = Timestamp.now()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TeacherCreateNoticeDialog(
    teacher: Teacher,
    assignedClasses: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSave: (Notice) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val cls = assignedClasses.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post Notice for ${cls?.name ?: "Class"}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                authorId = teacher.uid,
                                targetRole = "STUDENT",
                                published = true
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
