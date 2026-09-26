package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User
import com.example.ui.theme.DiscoveryGold
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.DiscoveryGreenDark
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.example.ui.viewmodel.StudentTab

@Composable
fun StudentDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.studentTab.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val notices by viewModel.notices.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Student Header
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
                            .background(Color(0xFF2563EB).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Student Portal", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2563EB), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("STUDENT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Text(user.displayName.ifBlank { "Siyabonga Dlamini" } + " • Grade 4A", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }

                IconButton(onClick = { viewModel.signOut() }) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = Color.White)
                }
            }
        }

        // Student Tabs
        ScrollableTabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = Color.White,
            contentColor = DiscoveryGreen
        ) {
            StudentTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { viewModel.setStudentTab(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize().weight(1f).padding(16.dp)) {
            when (currentTab) {
                StudentTab.MY_SUBJECTS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("My Grade 4 Subjects", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(subjects) { subj ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DiscoveryGreen.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Book, contentDescription = null, tint = DiscoveryGreen, modifier = Modifier.size(20.dp))
                                        }
                                        Column {
                                            Text(subj.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("CAPS Code: ${subj.code}", fontSize = 11.sp, color = SchoolSlate)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                StudentTab.ASSIGNMENTS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("My Homework & Class Tasks", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(assignments) { ass ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(ass.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DiscoveryGreen)
                                        Text(ass.description, fontSize = 11.sp, color = Color(0xFF334155))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Due Date: ${ass.dueDate?.toDate()?.toString()?.take(16) ?: "This Week"}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                    }
                                }
                            }
                        }
                    }
                }
                StudentTab.NOTICES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("School Announcements", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(notices) { not ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(not.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(not.message, fontSize = 11.sp, color = SchoolSlate)
                                    }
                                }
                            }
                        }
                    }
                }
                StudentTab.PROFILE -> {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF2563EB)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                                Text(user.displayName.ifBlank { "Siyabonga Dlamini" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(user.email, fontSize = 11.sp, color = SchoolSlate)
                                Text("Admission #: DPS-4012 • Grade 4A", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
                            }
                        }

                        Button(
                            onClick = { viewModel.signOut() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign Out", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
