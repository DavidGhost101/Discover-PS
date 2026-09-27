package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthenticationRepository
import com.example.data.auth.FirebaseAuthenticationRepository
import com.example.data.auth.SessionState
import com.example.data.model.Assignment
import com.example.data.model.Notice
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolSettings
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.Teacher
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.FirebaseSchoolRepository
import com.example.data.repository.Remote
import com.example.domain.FormValidation
import com.google.firebase.auth.PhoneAuthCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Initializing : AuthUiState
    data object NotConfigured : AuthUiState
    data object SignedOut : AuthUiState
    data class VerifyEmail(val email: String) : AuthUiState
    data class Authenticated(val user: User, val role: UserRole) : AuthUiState
    data class AccessDenied(val reason: String) : AuthUiState
    data class SessionError(val message: String) : AuthUiState
}

data class StatusMessage(val text: String, val isError: Boolean)

/** Everything the signed-in user is allowed to see, straight from Firestore listeners. */
data class SchoolState(
    val users: Remote<User> = Remote.idle(),
    val teachers: Remote<Teacher> = Remote.idle(),
    val teacherProfile: Remote<Teacher> = Remote.idle(),
    val students: Remote<Student> = Remote.idle(),
    val classes: Remote<SchoolClass> = Remote.idle(),
    val subjects: Remote<Subject> = Remote.idle(),
    val assignments: Remote<Assignment> = Remote.idle(),
    val notices: Remote<Notice> = Remote.idle(),
    val settings: Remote<SchoolSettings> = Remote.idle()
) {
    val schoolSettings: SchoolSettings
        get() = settings.items.firstOrNull() ?: SchoolSettings()
}

enum class AdminTab(val title: String) {
    DASHBOARD("Overview"),
    USERS("Users & Parents"),
    TEACHERS("Teachers"),
    STUDENTS("Students"),
    CLASSES("Classes"),
    SUBJECTS("Subjects"),
    ASSIGNMENTS("Assignments"),
    NOTICES("Notices"),
    SETTINGS("Settings")
}

enum class TeacherTab(val title: String) {
    MY_CLASSES("My Classes"),
    MY_STUDENTS("My Students"),
    ASSIGNMENTS("Assignments"),
    NOTICES("Notices"),
    PROFILE("Profile")
}

enum class StudentTab(val title: String) {
    MY_SUBJECTS("My Subjects"),
    ASSIGNMENTS("Assignments"),
    NOTICES("Notices"),
    PROFILE("My Profile")
}

enum class ParentTab(val title: String) {
    LEARNER("My Children"),
    HOMEWORK("Homework & Tasks"),
    NOTICES("School Notices"),
    PROFILE("Parent Profile")
}

enum class StaffTab(val title: String) {
    NOTICES("School Notices"),
    DIRECTORY("Staff Directory"),
    PROFILE("Profile")
}

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository: AuthenticationRepository = FirebaseAuthenticationRepository(application)
    private val repository = FirebaseSchoolRepository()

    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Initializing)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    /** Busy flag + message for the sign-in / registration / verification screens. */
    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()
    private val _authMessage = MutableStateFlow<StatusMessage?>(null)
    val authMessage: StateFlow<StatusMessage?> = _authMessage.asStateFlow()

    private val _school = MutableStateFlow(SchoolState())
    val school: StateFlow<SchoolState> = _school.asStateFlow()

    /** True while a write is in flight; forms disable their submit button (no double submits). */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _statusMessage = MutableStateFlow<StatusMessage?>(null)
    val statusMessage: StateFlow<StatusMessage?> = _statusMessage.asStateFlow()

    private val _adminTab = MutableStateFlow(AdminTab.DASHBOARD)
    val adminTab: StateFlow<AdminTab> = _adminTab.asStateFlow()
    private val _teacherTab = MutableStateFlow(TeacherTab.MY_CLASSES)
    val teacherTab: StateFlow<TeacherTab> = _teacherTab.asStateFlow()
    private val _studentTab = MutableStateFlow(StudentTab.MY_SUBJECTS)
    val studentTab: StateFlow<StudentTab> = _studentTab.asStateFlow()
    private val _parentTab = MutableStateFlow(ParentTab.LEARNER)
    val parentTab: StateFlow<ParentTab> = _parentTab.asStateFlow()
    private val _staffTab = MutableStateFlow(StaffTab.NOTICES)
    val staffTab: StateFlow<StaffTab> = _staffTab.asStateFlow()

    private var dataJob: Job? = null
    private var dataKey: Pair<String, UserRole>? = null

    init {
        viewModelScope.launch {
            authRepository.session.collect { onSession(it) }
        }
    }

    override fun onCleared() {
        stopData()
        authRepository.close()
        super.onCleared()
    }

    // ==========================================
    // SESSION
    // ==========================================

    private fun onSession(session: SessionState) {
        _authUiState.value = when (session) {
            SessionState.Initializing -> AuthUiState.Initializing
            SessionState.NotConfigured -> { stopData(); AuthUiState.NotConfigured }
            SessionState.SignedOut -> { stopData(); AuthUiState.SignedOut }
            is SessionState.VerifyEmail -> { stopData(); AuthUiState.VerifyEmail(session.email) }
            is SessionState.NoProfile -> {
                stopData()
                AuthUiState.AccessDenied("This login has no Discovery Primary profile. Ask the school administrator to create or link your account.")
            }
            is SessionState.Failed -> { stopData(); AuthUiState.SessionError(session.message) }
            is SessionState.Ready -> {
                val user = session.user
                val role = user.userRole
                when {
                    role == null -> {
                        stopData()
                        AuthUiState.AccessDenied("Your account has no valid role ('${user.role}'). Contact the school administrator.")
                    }
                    !user.active -> {
                        stopData()
                        AuthUiState.AccessDenied(
                            "Your account is not active. New registrations must be approved by the school " +
                                "administrator before you can see school information. If your account was " +
                                "deactivated, please contact the school office."
                        )
                    }
                    else -> {
                        startData(user.uid, role)
                        AuthUiState.Authenticated(user, role)
                    }
                }
            }
        }
    }

    private fun stopData() {
        dataJob?.cancel()
        dataJob = null
        dataKey = null
        _school.value = SchoolState()
    }

    /** Starts exactly one set of listeners for the (uid, role) pair; restarting on change
     *  cancels the previous scope, which removes every snapshot listener it owned. */
    private fun startData(uid: String, role: UserRole) {
        if (dataKey == uid to role && dataJob?.isActive == true) return
        stopData()
        dataKey = uid to role
        _school.value = loadingStateFor(role)
        dataJob = viewModelScope.launch {
            fun <T> bind(flow: Flow<Remote<T>>, reducer: SchoolState.(Remote<T>) -> SchoolState) {
                launch { flow.collect { remote -> _school.update { state -> reducer(state, remote) } } }
            }
            bind(repository.settings()) { copy(settings = it) }
            when (role) {
                UserRole.ADMIN -> {
                    bind(repository.allUsers()) { copy(users = it) }
                    bind(repository.teachers()) { copy(teachers = it) }
                    bind(repository.allStudents()) { copy(students = it) }
                    bind(repository.classes()) { copy(classes = it) }
                    bind(repository.subjects()) { copy(subjects = it) }
                    bind(repository.assignments()) { copy(assignments = it) }
                    bind(repository.allNotices()) { copy(notices = it) }
                }
                UserRole.TEACHER -> {
                    val profile = repository.teacher(uid).shareIn(this, SharingStarted.Eagerly, replay = 1)
                    bind(profile) { copy(teacherProfile = it) }
                    bind(
                        profile.map { it.items.firstOrNull()?.classIds.orEmpty() }
                            .distinctUntilChanged()
                            .flatMapLatest { repository.studentsInClasses(it) }
                    ) { copy(students = it) }
                    bind(repository.teachers()) { copy(teachers = it) }
                    bind(repository.classes()) { copy(classes = it) }
                    bind(repository.subjects()) { copy(subjects = it) }
                    bind(repository.assignments()) { copy(assignments = it) }
                    bind(mergeNotices(repository.noticesFor(role), repository.noticesAuthoredBy(uid))) { copy(notices = it) }
                }
                UserRole.PARENT -> {
                    bind(repository.childrenOf(uid)) { copy(students = it) }
                    bind(repository.teachers()) { copy(teachers = it) }
                    bind(repository.classes()) { copy(classes = it) }
                    bind(repository.subjects()) { copy(subjects = it) }
                    bind(repository.assignments()) { copy(assignments = it) }
                    bind(repository.noticesFor(role)) { copy(notices = it) }
                }
                UserRole.STUDENT -> {
                    bind(repository.studentRecordsOf(uid)) { copy(students = it) }
                    bind(repository.teachers()) { copy(teachers = it) }
                    bind(repository.classes()) { copy(classes = it) }
                    bind(repository.subjects()) { copy(subjects = it) }
                    bind(repository.assignments()) { copy(assignments = it) }
                    bind(repository.noticesFor(role)) { copy(notices = it) }
                }
                UserRole.STAFF -> {
                    bind(repository.teachers()) { copy(teachers = it) }
                    bind(repository.noticesFor(role)) { copy(notices = it) }
                }
            }
        }
    }

    private fun loadingStateFor(role: UserRole): SchoolState {
        fun <T> loading(): Remote<T> = Remote(loading = true)
        val base = SchoolState(settings = loading())
        return when (role) {
            UserRole.ADMIN -> base.copy(users = loading(), teachers = loading(), students = loading(), classes = loading(),
                subjects = loading(), assignments = loading(), notices = loading())
            UserRole.TEACHER -> base.copy(teacherProfile = loading(), teachers = loading(), students = loading(),
                classes = loading(), subjects = loading(), assignments = loading(), notices = loading())
            UserRole.PARENT, UserRole.STUDENT -> base.copy(students = loading(), teachers = loading(), classes = loading(),
                subjects = loading(), assignments = loading(), notices = loading())
            UserRole.STAFF -> base.copy(teachers = loading(), notices = loading())
        }
    }

    private fun mergeNotices(a: Flow<Remote<Notice>>, b: Flow<Remote<Notice>>): Flow<Remote<Notice>> =
        combine(a, b) { x, y ->
            Remote(
                items = (x.items + y.items).distinctBy { it.id },
                loading = x.loading || y.loading,
                error = x.error ?: y.error
            )
        }

    private fun currentUser(): User? = (_authUiState.value as? AuthUiState.Authenticated)?.user

    // ==========================================
    // AUTH ACTIONS
    // ==========================================

    private fun authAction(block: suspend () -> Result<*>, success: String? = null) {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authMessage.value = null
            val result = block()
            _authBusy.value = false
            result.onSuccess { if (success != null) _authMessage.value = StatusMessage(success, false) }
                .onFailure { _authMessage.value = StatusMessage(it.message ?: "Something went wrong.", true) }
        }
    }

    fun signIn(email: String, password: String) = authAction({ authRepository.signInWithEmail(email, password) })

    fun register(displayName: String, email: String, password: String) = authAction(
        { authRepository.registerParent(displayName, email, password) },
        "Account created. Check your inbox to verify your email address."
    )

    fun signInWithPhoneCredential(credential: PhoneAuthCredential) =
        authAction({ authRepository.signInWithPhoneCredential(credential) })

    fun sendPasswordReset(email: String) = authAction(
        { authRepository.sendPasswordReset(email) },
        "If an account exists for $email, a password reset link has been sent."
    )

    fun resendVerificationEmail() = authAction(
        { authRepository.resendEmailVerification() },
        "Verification email sent. Check your inbox (and spam folder)."
    )

    fun checkEmailVerified() {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            authRepository.refreshEmailVerification()
                .onSuccess { verified ->
                    if (!verified) _authMessage.value = StatusMessage("Your email address is not verified yet.", true)
                }
                .onFailure { _authMessage.value = StatusMessage(it.message ?: "Could not refresh.", true) }
            _authBusy.value = false
        }
    }

    fun showAuthError(message: String) {
        _authMessage.value = StatusMessage(message, true)
    }

    fun clearAuthMessage() {
        _authMessage.value = null
    }

    fun signOut() {
        stopData()
        authRepository.signOut()
        _adminTab.value = AdminTab.DASHBOARD
        _teacherTab.value = TeacherTab.MY_CLASSES
        _studentTab.value = StudentTab.MY_SUBJECTS
        _parentTab.value = ParentTab.LEARNER
        _staffTab.value = StaffTab.NOTICES
        _statusMessage.value = null
        _authMessage.value = null
        _formError.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearFormError() {
        _formError.value = null
    }

    fun setAdminTab(tab: AdminTab) { _adminTab.value = tab }
    fun setTeacherTab(tab: TeacherTab) { _teacherTab.value = tab }
    fun setStudentTab(tab: StudentTab) { _studentTab.value = tab }
    fun setParentTab(tab: ParentTab) { _parentTab.value = tab }
    fun setStaffTab(tab: StaffTab) { _staffTab.value = tab }

    // ==========================================
    // WRITES
    // ==========================================

    /** Runs one write at a time; reports the real outcome and only closes the form on success. */
    private fun perform(success: String, onSuccess: () -> Unit = {}, block: suspend () -> Result<*>) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _formError.value = null
            val result = try {
                block()
            } finally {
                _saving.value = false
            }
            result.onSuccess {
                _statusMessage.value = StatusMessage(success, false)
                onSuccess()
            }.onFailure {
                val message = it.message ?: "The change could not be saved."
                _formError.value = message
                _statusMessage.value = StatusMessage(message, true)
            }
        }
    }

    private fun fail(message: String): Result<Unit> = Result.failure(IllegalArgumentException(message))

    fun updateOwnProfile(displayName: String, phoneNumber: String, onSuccess: () -> Unit = {}) {
        val me = currentUser() ?: return
        perform("Profile updated", onSuccess) { repository.updateOwnProfile(me.uid, displayName, phoneNumber) }
    }

    fun sendMyPasswordReset() {
        val me = currentUser() ?: return
        if (me.email.isBlank()) {
            _statusMessage.value = StatusMessage("This account signs in by phone and has no password.", true)
            return
        }
        perform("Password reset link sent to ${me.email}") { authRepository.sendPasswordReset(me.email) }
    }

    // ---------- Admin: accounts ----------

    fun createAccount(email: String, displayName: String, role: UserRole, phone: String, onSuccess: () -> Unit = {}) =
        perform("Account created. A link to set a password was emailed to $email", onSuccess) {
            authRepository.provisionAccount(email, displayName, role, phone)
        }

    fun updateUser(user: User, onSuccess: () -> Unit = {}) {
        val me = currentUser() ?: return
        if (user.isProtectedOwner) {
            if (user.uid == me.uid) {
                perform("Profile updated", onSuccess) { repository.updateOwnProfile(me.uid, user.displayName, user.phoneNumber) }
            } else {
                perform("User ${user.label} updated", onSuccess) { repository.adminUpdateUserOnServer(user) }
            }
            return
        }
        if (user.uid == me.uid && (user.role != UserRole.ADMIN.name || !user.active)) {
            _formError.value = "You cannot remove your own administrator access. Ask another administrator."
            return
        }
        perform("User ${user.label} updated", onSuccess) { repository.updateUser(user) }
    }

    fun sendPasswordResetFor(user: User) {
        if (user.email.isBlank()) {
            _statusMessage.value = StatusMessage("${user.label} signs in by phone; there is no password to reset.", true)
            return
        }
        perform("Password reset link sent to ${user.email}") { authRepository.sendPasswordReset(user.email) }
    }

    // ---------- Admin: teachers ----------

    fun createTeacher(teacher: Teacher, onSuccess: () -> Unit = {}) = perform(
        "Teacher ${teacher.name} created. A link to set a password was emailed to ${teacher.email}", onSuccess
    ) {
        if (FormValidation.required(teacher.firstName, "First name") != null) return@perform fail("First name is required.")
        authRepository.provisionAccount(teacher.email, teacher.name, UserRole.TEACHER, teacher.phoneNumber)
            .fold({ uid -> repository.saveTeacher(teacher.copy(uid = uid, userId = uid), previous = null) }, { Result.failure<Unit>(it) })
    }

    fun saveTeacher(teacher: Teacher, previous: Teacher?, onSuccess: () -> Unit = {}) =
        perform("Teacher ${teacher.name} saved", onSuccess) { repository.saveTeacher(teacher, previous) }

    fun deleteTeacher(teacher: Teacher) =
        perform("Teacher ${teacher.name} removed from classes and subjects") { repository.deleteTeacher(teacher) }

    // ---------- Admin: students ----------

    fun saveStudent(student: Student, onSuccess: () -> Unit = {}) =
        perform("Learner ${student.fullName} saved", onSuccess) { repository.saveStudent(student) }

    fun deleteStudent(student: Student) =
        perform("Learner ${student.fullName} removed") { repository.deleteStudent(student.uid) }

    /** Creates a PARENT login and links it to [student] in one step. */
    fun createParentForStudent(student: Student, name: String, email: String, phone: String, onSuccess: () -> Unit = {}) =
        perform("Parent account created for ${student.fullName}. A password link was emailed to $email", onSuccess) {
            authRepository.provisionAccount(email, name, UserRole.PARENT, phone).fold(
                { uid -> repository.saveStudent(student.copy(parentIds = student.parentIds + uid)) },
                { Result.failure<Unit>(it) }
            )
        }

    /** Creates a STUDENT login for [student] and links it as the learner's own account. */
    fun createStudentLogin(student: Student, email: String, onSuccess: () -> Unit = {}) =
        perform("Learner login created. A password link was emailed to $email", onSuccess) {
            authRepository.provisionAccount(email, student.fullName, UserRole.STUDENT, "").fold(
                { uid -> repository.saveStudent(student.copy(userId = uid, email = email.trim())) },
                { Result.failure<Unit>(it) }
            )
        }

    // ---------- Admin: classes & subjects ----------

    fun saveClass(schoolClass: SchoolClass, previous: SchoolClass?, onSuccess: () -> Unit = {}) =
        perform("Class ${schoolClass.name} saved", onSuccess) { repository.saveClass(schoolClass, previous) }

    fun deleteClass(schoolClass: SchoolClass) {
        val enrolled = _school.value.students.items.count { it.classId == schoolClass.id }
        perform("Class ${schoolClass.name} deleted") { repository.deleteClass(schoolClass, enrolled) }
    }

    fun saveSubject(subject: Subject, previous: Subject?, classIds: List<String>, onSuccess: () -> Unit = {}) {
        val previousClassIds = previous?.let { p -> _school.value.classes.items.filter { p.id in it.subjectIds }.map { it.id } }.orEmpty()
        perform("Subject ${subject.name} saved", onSuccess) {
            repository.saveSubject(subject, previous, classIds, previousClassIds)
        }
    }

    fun deleteSubject(subject: Subject) {
        val classIds = _school.value.classes.items.filter { subject.id in it.subjectIds }.map { it.id }
        perform("Subject ${subject.name} deleted") { repository.deleteSubject(subject, classIds) }
    }

    // ---------- Assignments & notices (admin and teacher) ----------

    fun saveAssignment(assignment: Assignment, onSuccess: () -> Unit = {}) {
        val me = currentUser() ?: return
        val item = if (me.userRole == UserRole.TEACHER) assignment.copy(teacherId = me.uid) else assignment
        perform(if (assignment.id.isBlank()) "Assignment published to class" else "Assignment updated", onSuccess) {
            repository.saveAssignment(item)
        }
    }

    fun deleteAssignment(assignment: Assignment) =
        perform("Assignment \"${assignment.title}\" deleted") { repository.deleteAssignment(assignment.id) }

    fun saveNotice(notice: Notice, onSuccess: () -> Unit = {}) {
        val me = currentUser() ?: return
        val item = if (notice.id.isBlank() || me.userRole == UserRole.TEACHER) notice.copy(authorId = me.uid) else notice
        val message = when {
            !item.published -> "Notice saved as draft"
            notice.id.isBlank() -> "Notice published"
            else -> "Notice updated"
        }
        perform(message, onSuccess) { repository.saveNotice(item) }
    }

    fun deleteNotice(notice: Notice) =
        perform("Notice \"${notice.title}\" deleted") { repository.deleteNotice(notice.id) }

    fun saveSettings(settings: SchoolSettings) =
        perform("School settings updated") { repository.saveSettings(settings) }
}
