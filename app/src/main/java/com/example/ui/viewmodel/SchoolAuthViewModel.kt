package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Authenticated(val user: User, val role: UserRole) : AuthUiState
    data class AccessDenied(val reason: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

enum class AdminTab(val title: String) {
    DASHBOARD("Overview"),
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
    LEARNER("Learner Roster"),
    HOMEWORK("Homework & Tasks"),
    NOTICES("School Notices"),
    PROFILE("Parent Profile")
}

enum class StaffTab(val title: String) {
    DUTIES("Campus Duties"),
    NOTICES("School Notices"),
    DIRECTORY("Staff Directory"),
    PROFILE("Profile")
}

class SchoolAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseSchoolRepository(application)

    // Auth State
    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    // Navigation State for each role
    private val _adminTab = MutableStateFlow(AdminTab.DASHBOARD)
    val adminTab: StateFlow<AdminTab> = _adminTab.asStateFlow()

    private val _teacherTab = MutableStateFlow(TeacherTab.MY_CLASSES)
    val teacherTab: StateFlow<TeacherTab> = _teacherTab.asStateFlow()

    private val _studentTab = MutableStateFlow(StudentTab.MY_SUBJECTS)
    val studentTab: StateFlow<StudentTab> = _studentTab.asStateFlow()

    private val _parentTab = MutableStateFlow(ParentTab.LEARNER)
    val parentTab: StateFlow<ParentTab> = _parentTab.asStateFlow()

    private val _staffTab = MutableStateFlow(StaffTab.DUTIES)
    val staffTab: StateFlow<StaffTab> = _staffTab.asStateFlow()

    // Status Message / Notification
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Data streams from Firestore
    val teachers: StateFlow<List<Teacher>> = repository.getTeachersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val students: StateFlow<List<Student>> = repository.getStudentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val classes: StateFlow<List<SchoolClass>> = repository.getClassesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjects: StateFlow<List<Subject>> = repository.getSubjectsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assignments: StateFlow<List<Assignment>> = repository.getAssignmentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notices: StateFlow<List<Notice>> = repository.getNoticesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<SchoolSettings> = repository.getSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SchoolSettings())

    init {
        // Observe repository user doc
        viewModelScope.launch {
            repository.currentUserDoc.collect { user ->
                if (user != null) {
                    val role = user.userRole
                    if (!user.active) {
                        _authUiState.value = AuthUiState.AccessDenied("This account has been deactivated. Please contact the administrator.")
                    } else if (role == null) {
                        _authUiState.value = AuthUiState.AccessDenied("No valid application role assigned to this account in Firestore.")
                    } else {
                        _authUiState.value = AuthUiState.Authenticated(user, role)
                    }
                } else {
                    _authUiState.value = AuthUiState.Idle
                }
            }
        }
    }

    // ==========================================
    // AUTH ACTIONS
    // ==========================================

    fun signIn(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authUiState.value = AuthUiState.Error("Please enter both email and password")
            return
        }
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = repository.signIn(email, pass)
            result.onSuccess { user ->
                val role = user.userRole
                if (!user.active) {
                    _authUiState.value = AuthUiState.AccessDenied("Account is deactivated.")
                } else if (role == null) {
                    _authUiState.value = AuthUiState.AccessDenied("Access Denied: Unrecognized role '${user.role}'.")
                } else {
                    _authUiState.value = AuthUiState.Authenticated(user, role)
                }
            }.onFailure { err ->
                _authUiState.value = AuthUiState.Error(err.message ?: "Authentication failed")
            }
        }
    }

    fun register(email: String, pass: String, displayName: String, role: String) {
        if (email.isBlank() || pass.isBlank() || displayName.isBlank()) {
            _authUiState.value = AuthUiState.Error("Please fill in all registration fields.")
            return
        }
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = repository.signUp(email, pass, displayName, role)
            result.onSuccess { user ->
                val r = user.userRole ?: UserRole.STUDENT
                _authUiState.value = AuthUiState.Authenticated(user, r)
            }.onFailure { err ->
                _authUiState.value = AuthUiState.Error(err.message ?: "Registration failed")
            }
        }
    }

    fun verifyPhoneOtp(verificationId: String, code: String) {
        if (code.length != 6) {
            _authUiState.value = AuthUiState.Error("Please enter the 6-digit SMS verification code.")
            return
        }
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            val result = repository.verifyPhoneOtp(verificationId, code)
            result.onSuccess { user ->
                val r = user.userRole ?: UserRole.PARENT
                _authUiState.value = AuthUiState.Authenticated(user, r)
            }.onFailure { err ->
                _authUiState.value = AuthUiState.Error(err.message ?: "Phone verification failed")
            }
        }
    }

    // Quick Login Shortcuts for Verification Testing
    fun quickLoginAdmin() {
        signIn("admin@discoveryprimary.co.za", "AdminPass123!")
    }

    fun quickLoginTeacher(email: String = "teacher.khumalo@discoveryprimary.co.za") {
        signIn(email, "TeacherPass123!")
    }

    fun quickLoginStudent() {
        signIn("siyabonga.student@discoveryprimary.co.za", "StudentPass123!")
    }

    fun quickLoginParent() {
        signIn("parent.dlamini@discoveryprimary.co.za", "ParentPass123!")
    }

    fun quickLoginStaff() {
        signIn("staff.clinic@discoveryprimary.co.za", "StaffPass123!")
    }

    fun signOut() {
        repository.signOut()
        _adminTab.value = AdminTab.DASHBOARD
        _teacherTab.value = TeacherTab.MY_CLASSES
        _studentTab.value = StudentTab.MY_SUBJECTS
        _parentTab.value = ParentTab.LEARNER
        _staffTab.value = StaffTab.DUTIES
        _authUiState.value = AuthUiState.Idle
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setAdminTab(tab: AdminTab) {
        _adminTab.value = tab
    }

    fun setTeacherTab(tab: TeacherTab) {
        _teacherTab.value = tab
    }

    fun setStudentTab(tab: StudentTab) {
        _studentTab.value = tab
    }

    fun setParentTab(tab: ParentTab) {
        _parentTab.value = tab
    }

    fun setStaffTab(tab: StaffTab) {
        _staffTab.value = tab
    }

    // ==========================================
    // TEACHER DERIVED DATA
    // ==========================================

    fun getTeacherProfile(user: User): Teacher {
        return teachers.value.find { it.email.equals(user.email, ignoreCase = true) || it.uid == user.uid }
            ?: Teacher(
                uid = "teach_active",
                userId = user.uid,
                firstName = user.displayName.substringBefore(" "),
                lastName = user.displayName.substringAfter(" ", "Teacher"),
                email = user.email,
                employeeNumber = "DPS-EMP-102",
                classIds = listOf("class_4a")
            )
    }

    fun getTeacherClasses(user: User): List<SchoolClass> {
        val teacher = getTeacherProfile(user)
        val assignedIds = teacher.classIds
        val byTeacher = classes.value.filter { it.teacherIds.contains(teacher.uid) || assignedIds.contains(it.id) }
        return if (byTeacher.isEmpty()) classes.value.take(2) else byTeacher
    }

    fun getTeacherStudents(user: User): List<Student> {
        val assignedClassIds = getTeacherClasses(user).map { it.id }.toSet()
        return students.value.filter { assignedClassIds.contains(it.classId) || assignedClassIds.isEmpty() }
    }

    fun getTeacherAssignments(user: User): List<Assignment> {
        val assignedClassIds = getTeacherClasses(user).map { it.id }.toSet()
        val teacher = getTeacherProfile(user)
        return assignments.value.filter { it.teacherId == teacher.uid || assignedClassIds.contains(it.classId) }
    }

    fun getTeacherNotices(user: User): List<Notice> {
        return notices.value.filter { it.targetRole == "ALL" || it.targetRole == "TEACHER" }
    }

    // ==========================================
    // CRUD OPERATIONS
    // ==========================================

    fun saveTeacher(teacher: Teacher) {
        viewModelScope.launch {
            repository.saveTeacher(teacher)
            _statusMessage.value = "Teacher record saved successfully"
        }
    }

    fun deleteTeacher(teacherUid: String) {
        viewModelScope.launch {
            repository.deleteTeacher(teacherUid)
            _statusMessage.value = "Teacher record removed"
        }
    }

    fun saveStudent(student: Student) {
        viewModelScope.launch {
            repository.saveStudent(student)
            _statusMessage.value = "Student record saved successfully"
        }
    }

    fun deleteStudent(studentUid: String) {
        viewModelScope.launch {
            repository.deleteStudent(studentUid)
            _statusMessage.value = "Student record removed"
        }
    }

    fun saveClass(cls: SchoolClass) {
        viewModelScope.launch {
            repository.saveClass(cls)
            _statusMessage.value = "Class saved successfully"
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            repository.deleteClass(classId)
            _statusMessage.value = "Class removed"
        }
    }

    fun saveSubject(subject: Subject) {
        viewModelScope.launch {
            repository.saveSubject(subject)
            _statusMessage.value = "Subject saved successfully"
        }
    }

    fun deleteSubject(subjectId: String) {
        viewModelScope.launch {
            repository.deleteSubject(subjectId)
            _statusMessage.value = "Subject removed"
        }
    }

    fun saveAssignment(assignment: Assignment) {
        viewModelScope.launch {
            repository.saveAssignment(assignment)
            _statusMessage.value = "Assignment published to class"
        }
    }

    fun deleteAssignment(assignmentId: String) {
        viewModelScope.launch {
            repository.deleteAssignment(assignmentId)
            _statusMessage.value = "Assignment removed"
        }
    }

    fun saveNotice(notice: Notice) {
        viewModelScope.launch {
            repository.saveNotice(notice)
            _statusMessage.value = "Notice published successfully"
        }
    }

    fun deleteNotice(noticeId: String) {
        viewModelScope.launch {
            repository.deleteNotice(noticeId)
            _statusMessage.value = "Notice removed"
        }
    }

    fun saveSettings(settings: SchoolSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
            _statusMessage.value = "School settings updated"
        }
    }
}
