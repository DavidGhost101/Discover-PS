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
import androidx.compose.material.icons.filled.Badge
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
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.domain.SchoolAccess
import com.example.ui.components.InfoCard
import com.example.ui.components.PortalHeader
import com.example.ui.components.PortalTabs
import com.example.ui.components.ProfileCard
import com.example.ui.components.RemoteContent
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusBanner
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.example.ui.viewmodel.StaffTab

private val StaffGreen = Color(0xFF059669)

@Composable
fun StaffDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.staffTab.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val state by viewModel.school.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        PortalHeader(
            title = "Support Staff Portal",
            badge = "STAFF",
            badgeColor = StaffGreen,
            subtitle = user.label,
            icon = Icons.Default.Badge,
            onSignOut = { viewModel.signOut() }
        )
        StatusBanner(statusMsg) { viewModel.clearStatusMessage() }
        PortalTabs(StaffTab.entries, currentTab, { it.title }) { viewModel.setStaffTab(it) }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (currentTab) {
                StaffTab.NOTICES -> {
                    val notices = SchoolAccess.visibleNotices(state.notices.items, UserRole.STAFF, user.uid, emptySet())
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("School Announcements")
                        RemoteContent(state.notices, "No announcements for staff right now.", notices) { list ->
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(list, key = { it.id }) { n -> NoticeCard(n, null, showAudience = false) }
                            }
                        }
                    }
                }
                StaffTab.DIRECTORY -> {
                    val teachers = state.teachers.items.sortedBy { it.name }
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Academic Staff Directory (${teachers.size})")
                        RemoteContent(state.teachers, "No teachers listed yet.", teachers) { list ->
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(list, key = { it.uid }) { teacher ->
                                    InfoCard {
                                        Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.testTag("directory_teacher_name"))
                                        Text(
                                            listOf(teacher.employeeNumber.takeIf { it.isNotBlank() }?.let { "Emp #: $it" }, teacher.email, teacher.phoneNumber)
                                                .filterNotNull().filter { it.isNotBlank() }.joinToString(" • "),
                                            fontSize = 11.sp, color = SchoolSlate
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                StaffTab.PROFILE -> {
                    val saving by viewModel.saving.collectAsState()
                    val error by viewModel.formError.collectAsState()
                    ProfileCard(
                        name = user.displayName,
                        email = user.email,
                        phone = user.phoneNumber,
                        roleLabel = "STAFF",
                        accent = StaffGreen,
                        saving = saving,
                        error = error,
                        details = emptyList(),
                        onSave = { name, phone, done -> viewModel.updateOwnProfile(name, phone, done) },
                        onResetPassword = { viewModel.sendMyPasswordReset() },
                        onSignOut = { viewModel.signOut() }
                    )
                }
            }
        }
    }
}
