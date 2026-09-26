package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DiscoveryGold
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.DiscoveryGreenDark
import com.example.ui.viewmodel.SchoolAuthViewModel

@Composable
private fun MessageScreen(
    icon: ImageVector,
    iconTint: Color,
    background: Color,
    title: String,
    tag: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(background).verticalScroll(rememberScrollState()).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth().testTag(tag)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(36.dp))
                }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), textAlign = TextAlign.Center)
                content()
            }
        }
    }
}

@Composable
fun AccessDeniedScreen(
    reason: String,
    viewModel: SchoolAuthViewModel
) {
    MessageScreen(Icons.Default.GppBad, Color(0xFFDC2626), Color(0xFFFEF2F2), "Access Denied", "access_denied_screen") {
        Text(reason, fontSize = 13.sp, color = Color(0xFF7F1D1D), textAlign = TextAlign.Center, lineHeight = 18.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = { viewModel.signOut() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("access_denied_sign_out")
        ) {
            Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Return to Sign In", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VerifyEmailScreen(email: String, viewModel: SchoolAuthViewModel) {
    val busy by viewModel.authBusy.collectAsState()
    val message by viewModel.authMessage.collectAsState()
    MessageScreen(Icons.Default.MarkEmailUnread, DiscoveryGreen, Color(0xFFF8F9FA), "Verify your email", "verify_email_screen") {
        Text(
            "We sent a verification link to $email. Open it, then tap \"I've verified my email\".",
            fontSize = 13.sp, color = Color(0xFF334155), textAlign = TextAlign.Center
        )
        message?.let {
            Text(it.text, fontSize = 12.sp, color = if (it.isError) Color(0xFFDC2626) else DiscoveryGreen, textAlign = TextAlign.Center)
        }
        Button(
            onClick = { viewModel.checkEmailVerified() },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
            modifier = Modifier.fillMaxWidth().testTag("verify_email_check_button")
        ) { Text(if (busy) "Checking…" else "I've verified my email") }
        OutlinedButton(
            onClick = { viewModel.resendVerificationEmail() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("verify_email_resend_button")
        ) { Text("Resend verification email") }
        OutlinedButton(onClick = { viewModel.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
fun NotConfiguredScreen() {
    MessageScreen(Icons.Default.SettingsSuggest, DiscoveryGold, Color(0xFFF8F9FA), "Firebase is not configured", "not_configured_screen") {
        Text(
            "This build has no Firebase project. Download google-services.json for the Android app " +
                "(package com.aistudio.discoveryprimary.kdpmsx) from the Firebase console, place it in the " +
                "app/ folder and rebuild. Sign-in is disabled until then.",
            fontSize = 13.sp, color = Color(0xFF334155), textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize().background(DiscoveryGreenDark), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.School, contentDescription = null, tint = DiscoveryGold, modifier = Modifier.size(48.dp))
            Text("Discovery Primary School", color = Color.White, fontWeight = FontWeight.Bold)
            CircularProgressIndicator(color = DiscoveryGold, modifier = Modifier.size(24.dp))
        }
    }
}
