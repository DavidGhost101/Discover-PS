package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.model.UserRole
import com.example.ui.screens.AccessDeniedScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ParentDashboardScreen
import com.example.ui.screens.StaffDashboardScreen
import com.example.ui.screens.StudentDashboardScreen
import com.example.ui.screens.TeacherDashboardScreen
import com.example.ui.theme.DiscoveryTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.SchoolAuthViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: SchoolAuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiscoveryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiscoverySchoolApp(viewModel = authViewModel)
                }
            }
        }
    }
}

@Composable
fun DiscoverySchoolApp(viewModel: SchoolAuthViewModel) {
    val authState by viewModel.authUiState.collectAsState()

    Crossfade(targetState = authState, label = "AuthFlowTransition") { state ->
        when (state) {
            is AuthUiState.Idle, is AuthUiState.Loading, is AuthUiState.Error -> {
                LoginScreen(viewModel = viewModel)
            }
            is AuthUiState.AccessDenied -> {
                AccessDeniedScreen(
                    reason = state.reason,
                    viewModel = viewModel
                )
            }
            is AuthUiState.Authenticated -> {
                when (state.role) {
                    UserRole.ADMIN -> {
                        AdminDashboardScreen(
                            user = state.user,
                            viewModel = viewModel
                        )
                    }
                    UserRole.TEACHER -> {
                        TeacherDashboardScreen(
                            user = state.user,
                            viewModel = viewModel
                        )
                    }
                    UserRole.STUDENT -> {
                        StudentDashboardScreen(
                            user = state.user,
                            viewModel = viewModel
                        )
                    }
                    UserRole.PARENT -> {
                        ParentDashboardScreen(
                            user = state.user,
                            viewModel = viewModel
                        )
                    }
                    UserRole.STAFF -> {
                        StaffDashboardScreen(
                            user = state.user,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}
