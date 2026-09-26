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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
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
import com.example.ui.viewmodel.StaffTab

@Composable
fun StaffDashboardScreen(
    user: User,
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.staffTab.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val notices by viewModel.notices.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Staff Header
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
                            .background(Color(0xFF059669).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Support Staff Portal", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF059669), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("STAFF", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Text(user.displayName.ifBlank { "Support Personnel" } + " • Campus Operations", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }

                IconButton(onClick = { viewModel.signOut() }) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out", tint = Color.White)
                }
            }
        }

        // Staff Tabs
        ScrollableTabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = Color.White,
            contentColor = DiscoveryGreen
        ) {
            StaffTab.values().forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { viewModel.setStaffTab(tab) },
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
                StaffTab.DUTIES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Operational Campus Duties", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)

                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Morning Gate & Visitor Registration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Main Reception Gate • 07:15 - 08:00 • Supervised", fontSize = 11.sp, color = SchoolSlate)
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Health & First Aid Station Inspection", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Campus Clinic • Daily 10:00 - 11:30", fontSize = 11.sp, color = SchoolSlate)
                            }
                        }
                    }
                }
                StaffTab.NOTICES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("School Announcements", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(notices) { not ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(not.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(not.message, fontSize = 11.sp, color = SchoolSlate)
                                    }
                                }
                            }
                        }
                    }
                }
                StaffTab.DIRECTORY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Academic Staff Directory (${teachers.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DiscoveryGreen)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(teachers) { teacher ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(DiscoveryGreen.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = DiscoveryGreen, modifier = Modifier.size(20.dp))
                                        }
                                        Column {
                                            Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Emp #: ${teacher.employeeNumber} • ${teacher.email}", fontSize = 11.sp, color = SchoolSlate)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                StaffTab.PROFILE -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF059669)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Badge, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                                Text(user.displayName.ifBlank { "Staff Member" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(user.email, fontSize = 11.sp, color = SchoolSlate)
                                Text("Department: Campus Operations (D12)", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
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
