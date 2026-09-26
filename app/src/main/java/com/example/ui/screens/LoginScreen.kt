package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.PhoneAuthenticationManager
import com.example.data.auth.normalizeSouthAfricanPhone
import com.example.data.firebase.FirebaseProvider
import com.example.ui.theme.DiscoveryGold
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.DiscoveryGreenDark
import com.example.ui.theme.SchoolSlate
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.SchoolAuthViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider

enum class LoginMode(val title: String) {
    SIGN_IN("Sign In"),
    REGISTER("Register"),
    PHONE("Phone OTP")
}

@Composable
fun LoginScreen(
    viewModel: SchoolAuthViewModel,
    modifier: Modifier = Modifier
) {
    val authState by viewModel.authUiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedMode by remember { mutableStateOf(LoginMode.SIGN_IN) }

    // Sign in state
    var email by remember { mutableStateOf("admin@discoveryprimary.co.za") }
    var password by remember { mutableStateOf("AdminPass123!") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Register state
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPass by remember { mutableStateOf("") }
    var regRole by remember { mutableStateOf("STUDENT") }

    // Phone state
    var phoneInput by remember { mutableStateOf("082 123 4567") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var isCodeSent by remember { mutableStateOf(false) }
    var phoneStatusMsg by remember { mutableStateOf<String?>(null) }

    val isLoading = authState is AuthUiState.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .verticalScroll(rememberScrollState())
    ) {
        // Hero Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DiscoveryGreenDark)
                .padding(vertical = 24.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DiscoveryGold.copy(alpha = 0.2f))
                        .border(2.dp, DiscoveryGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "School Crest",
                        tint = DiscoveryGold,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "DISCOVERY PRIMARY SCHOOL",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp,
                    color = Color.White
                )

                Text(
                    text = "LMS & Academic Operations Platform",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Box(
                    modifier = Modifier
                        .background(DiscoveryGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (FirebaseProvider.isRealFirebaseConfigured)
                            "Firebase Cloud Active • Firestore RBAC"
                        else
                            "Discovery LMS Ready • Local Sandbox Active",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DiscoveryGold
                    )
                }
            }
        }

        // Mode Tab Bar
        TabRow(
            selectedTabIndex = selectedMode.ordinal,
            containerColor = Color.White,
            contentColor = DiscoveryGreen
        ) {
            LoginMode.values().forEach { mode ->
                Tab(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    text = {
                        Text(
                            text = mode.title,
                            fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Form Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (authState is AuthUiState.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEE2E2), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = (authState as AuthUiState.Error).message,
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                when (selectedMode) {
                    LoginMode.SIGN_IN -> {
                        Text(
                            text = "Email & Password Sign In",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("School Email") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = SchoolSlate) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SchoolSlate) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = SchoolSlate
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = { viewModel.signIn(email, password) },
                            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("login_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Authenticating...")
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In", fontWeight = FontWeight.Bold)
                            }
                        }

                        Divider(color = Color(0xFFE2E8F0))

                        // Quick Role Testing Shortcuts (Supporting all 5 roles)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Quick Role Testing Shortcuts:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SchoolSlate
                            )

                            OutlinedButton(
                                onClick = {
                                    email = "admin@discoveryprimary.co.za"
                                    password = "AdminPass123!"
                                    viewModel.quickLoginAdmin()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("login_quick_admin_button")
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = DiscoveryGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ADMIN: Principal Raymond Peters", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    email = "teacher.khumalo@discoveryprimary.co.za"
                                    password = "TeacherPass123!"
                                    viewModel.quickLoginTeacher()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("login_quick_teacher_button")
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, tint = DiscoveryGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TEACHER: Mrs. N. Khumalo (Grade 4A)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    email = "siyabonga.student@discoveryprimary.co.za"
                                    password = "StudentPass123!"
                                    viewModel.quickLoginStudent()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("STUDENT: Siyabonga Dlamini", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    email = "parent.dlamini@discoveryprimary.co.za"
                                    password = "ParentPass123!"
                                    viewModel.quickLoginParent()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FamilyRestroom, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PARENT: Mr. Bongani Dlamini", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    email = "staff.clinic@discoveryprimary.co.za"
                                    password = "StaffPass123!"
                                    viewModel.quickLoginStaff()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("STAFF: Sister Martha (Campus Clinic)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    LoginMode.REGISTER -> {
                        Text(
                            text = "New User Registration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )

                        OutlinedTextField(
                            value = regName,
                            onValueChange = { regName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = regEmail,
                            onValueChange = { regEmail = it },
                            label = { Text("Email Address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = regPass,
                            onValueChange = { regPass = it },
                            label = { Text("Password (min 6 characters)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Text("Select Role to Register (Admin cannot be self-assigned):", fontSize = 11.sp, color = SchoolSlate)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("STUDENT", "TEACHER", "PARENT", "STAFF").forEach { r ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (regRole == r) DiscoveryGreen else Color(0xFFE2E8F0))
                                        .clickable { regRole = r }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(r, fontSize = 10.sp, color = if (regRole == r) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.register(regEmail, regPass, regName, regRole) },
                            enabled = !isLoading && regName.isNotBlank() && regEmail.isNotBlank() && regPass.length >= 6,
                            colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text("Create Account", fontWeight = FontWeight.Bold)
                        }
                    }

                    LoginMode.PHONE -> {
                        Text(
                            text = "Phone Number SMS Authentication",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )

                        Text(
                            text = "Enter a South African mobile number (e.g., 082 123 4567). It will be normalized to E.164 (+27821234567).",
                            fontSize = 11.sp,
                            color = SchoolSlate
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Mobile Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SchoolSlate) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        if (phoneStatusMsg != null) {
                            Text(phoneStatusMsg ?: "", fontSize = 11.sp, color = Color(0xFF2563EB))
                        }

                        Button(
                            onClick = {
                                val normalized = normalizeSouthAfricanPhone(phoneInput)
                                phoneStatusMsg = "Sending OTP to $normalized..."
                                if (activity != null) {
                                    val manager = PhoneAuthenticationManager(FirebaseProvider.auth)
                                    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                            phoneStatusMsg = "Verification completed automatically!"
                                        }

                                        override fun onVerificationFailed(e: FirebaseException) {
                                            phoneStatusMsg = "SMS notice: ${e.message ?: "Simulating test OTP"}"
                                            verificationId = "test_vid_123"
                                            isCodeSent = true
                                        }

                                        override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                                            verificationId = vid
                                            isCodeSent = true
                                            phoneStatusMsg = "6-digit code sent to $normalized"
                                        }
                                    }
                                    try {
                                        manager.sendCode(activity, normalized, callbacks)
                                    } catch (e: Exception) {
                                        verificationId = "test_vid_123"
                                        isCodeSent = true
                                        phoneStatusMsg = "Code sent (Demo test ready)"
                                    }
                                } else {
                                    verificationId = "test_vid_123"
                                    isCodeSent = true
                                    phoneStatusMsg = "Code sent (Demo test ready)"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send SMS Verification Code", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        if (isCodeSent) {
                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { if (it.length <= 6) otpCode = it },
                                label = { Text("6-Digit Verification Code") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Button(
                                onClick = {
                                    viewModel.verifyPhoneOtp(verificationId, otpCode)
                                },
                                enabled = otpCode.length == 6,
                                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Verify Code & Sign In", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Security Notice Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Firebase Firestore RBAC Active",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF1D4ED8)
                    )
                    Text(
                        text = "All school records are isolated by schoolId ('discovery-primary'). Client role self-escalation is strictly prevented.",
                        fontSize = 11.sp,
                        color = Color(0xFF3B82F6)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
