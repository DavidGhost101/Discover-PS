package com.example.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.friendlyError
import com.example.domain.FormValidation
import com.example.ui.theme.DiscoveryGold
import com.example.ui.theme.DiscoveryGreen
import com.example.ui.theme.DiscoveryGreenDark
import com.example.ui.theme.SchoolSlate
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
    val busy by viewModel.authBusy.collectAsState()
    val message by viewModel.authMessage.collectAsState()
    val activity = LocalActivity.current

    var selectedMode by rememberSaveable { mutableStateOf(LoginMode.SIGN_IN) }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var regName by rememberSaveable { mutableStateOf("") }
    var regEmail by rememberSaveable { mutableStateOf("") }
    var regPass by rememberSaveable { mutableStateOf("") }
    var regConfirm by rememberSaveable { mutableStateOf("") }

    var phoneInput by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf("") }
    var phoneStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var sendingCode by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(DiscoveryGreenDark).padding(vertical = 24.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DiscoveryGold.copy(alpha = 0.2f))
                        .border(2.dp, DiscoveryGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.School, contentDescription = "School Crest", tint = DiscoveryGold, modifier = Modifier.size(32.dp))
                }
                Text("DISCOVERY PRIMARY SCHOOL", fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp, color = Color.White)
                Text("LMS & Academic Operations Platform", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }

        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            TabRow(selectedTabIndex = selectedMode.ordinal, containerColor = Color.White, contentColor = DiscoveryGreen) {
                LoginMode.entries.forEach { mode ->
                    Tab(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode; viewModel.clearAuthMessage() },
                        text = { Text(mode.title, fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) },
                        modifier = Modifier.testTag("login_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    message?.let {
                        Text(
                            it.text,
                            color = if (it.isError) Color(0xFFDC2626) else Color(0xFF166534),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (it.isError) Color(0xFFFEE2E2) else Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .testTag(if (it.isError) "login_error" else "login_info")
                        )
                    }

                    when (selectedMode) {
                        LoginMode.SIGN_IN -> {
                            Text("Email & Password Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
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
                                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
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
                                enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("login_submit_button")
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Signing in…")
                                } else {
                                    Text("Sign In", fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(
                                onClick = {
                                    val error = FormValidation.email(email)
                                    if (error != null) viewModel.showAuthError("Enter your email address above, then tap \"Forgot password\".")
                                    else viewModel.sendPasswordReset(email)
                                },
                                enabled = !busy,
                                modifier = Modifier.align(Alignment.End).testTag("login_forgot_password_button")
                            ) { Text("Forgot password?", color = DiscoveryGreen) }
                        }

                        LoginMode.REGISTER -> {
                            Text("Parent / Guardian Registration", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                            Text(
                                "Creates a parent account. After you verify your email, the school administrator " +
                                    "approves the account and links your children. Teacher, staff and learner accounts " +
                                    "are issued by the school.",
                                fontSize = 11.sp, color = SchoolSlate
                            )
                            val nameError = if (regName.isNotEmpty() && regName.isBlank()) "Full name is required." else null
                            val emailError = if (regEmail.isNotEmpty()) FormValidation.email(regEmail) else null
                            val passError = if (regPass.isNotEmpty()) FormValidation.password(regPass) else null
                            val confirmError = if (regConfirm.isNotEmpty() && regConfirm != regPass) "Passwords do not match." else null
                            com.example.ui.components.FormField(regName, { regName = it }, "Full Name", "register_name_input", nameError)
                            com.example.ui.components.FormField(regEmail, { regEmail = it }, "Email Address", "register_email_input", emailError)
                            OutlinedTextField(
                                value = regPass,
                                onValueChange = { regPass = it },
                                label = { Text("Password (min ${FormValidation.MIN_PASSWORD_LENGTH}, letters + numbers)") },
                                isError = passError != null,
                                supportingText = if (passError != null) { { Text(passError) } } else null,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("register_password_input")
                            )
                            OutlinedTextField(
                                value = regConfirm,
                                onValueChange = { regConfirm = it },
                                label = { Text("Confirm password") },
                                isError = confirmError != null,
                                supportingText = if (confirmError != null) { { Text(confirmError) } } else null,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("register_confirm_input")
                            )
                            val canRegister = !busy && regName.isNotBlank() &&
                                FormValidation.email(regEmail) == null &&
                                FormValidation.password(regPass) == null &&
                                regConfirm == regPass
                            Button(
                                onClick = { viewModel.register(regName, regEmail, regPass) },
                                enabled = canRegister,
                                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("register_submit_button")
                            ) {
                                Text(if (busy) "Creating account…" else "Create Parent Account", fontWeight = FontWeight.Bold)
                            }
                        }

                        LoginMode.PHONE -> {
                            Text("Phone Number SMS Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
                            Text(
                                "For parents without email. Enter a South African mobile number (e.g. 082 123 4567). " +
                                    "New phone accounts must be approved by the school administrator.",
                                fontSize = 11.sp, color = SchoolSlate
                            )
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("Mobile Phone Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SchoolSlate) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("phone_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            phoneStatus?.let { Text(it, fontSize = 11.sp, color = Color(0xFF2563EB)) }
                            Button(
                                onClick = {
                                    val normalized = normalizeSouthAfricanPhone(phoneInput)
                                    val phoneError = FormValidation.phone(normalized)
                                    if (phoneError != null || normalized.isBlank()) {
                                        viewModel.showAuthError(phoneError ?: "Enter your mobile number.")
                                        return@Button
                                    }
                                    if (activity == null || !FirebaseProvider.isConfigured) {
                                        viewModel.showAuthError("Phone sign-in is unavailable on this device.")
                                        return@Button
                                    }
                                    sendingCode = true
                                    phoneStatus = "Sending code to $normalized…"
                                    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                            sendingCode = false
                                            phoneStatus = "Number verified automatically. Signing in…"
                                            viewModel.signInWithPhoneCredential(credential)
                                        }

                                        override fun onVerificationFailed(e: FirebaseException) {
                                            sendingCode = false
                                            phoneStatus = null
                                            viewModel.showAuthError("Could not send the SMS code: ${friendlyError(e)}")
                                        }

                                        override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                                            sendingCode = false
                                            verificationId = vid
                                            phoneStatus = "A 6-digit code was sent to $normalized"
                                        }
                                    }
                                    try {
                                        PhoneAuthenticationManager(FirebaseProvider.auth).sendCode(activity, normalized, callbacks)
                                    } catch (e: Exception) {
                                        sendingCode = false
                                        phoneStatus = null
                                        viewModel.showAuthError(friendlyError(e))
                                    }
                                },
                                enabled = !busy && !sendingCode && phoneInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGold),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("phone_send_code_button")
                            ) {
                                Text(if (verificationId.isBlank()) "Send SMS Code" else "Resend SMS Code", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            if (verificationId.isNotBlank()) {
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { input -> otpCode = input.filter { it.isDigit() }.take(6) },
                                    label = { Text("6-Digit Verification Code") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("phone_code_input"),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Button(
                                    onClick = {
                                        viewModel.signInWithPhoneCredential(PhoneAuthProvider.getCredential(verificationId, otpCode))
                                    },
                                    enabled = !busy && otpCode.length == 6,
                                    colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("phone_verify_button")
                                ) {
                                    Text(if (busy) "Verifying…" else "Verify Code & Sign In", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
