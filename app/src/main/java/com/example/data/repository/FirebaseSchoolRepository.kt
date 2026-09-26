package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.auth.FirebaseAuthenticationRepository
import com.example.data.firebase.FirebaseProvider
import com.example.data.model.Assignment
import com.example.data.model.Notice
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolSettings
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.Teacher
import com.example.data.model.User
import com.example.data.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class FirebaseSchoolRepository(private val context: Context) {
    companion object {
        private const val TAG = "FirebaseSchoolRepo"
        const val SCHOOL_ID = "discovery-primary"
        const val COLLECTION_USERS = "users"
        const val COLLECTION_TEACHERS = "teachers"
        const val COLLECTION_STUDENTS = "students"
        const val COLLECTION_CLASSES = "classes"
        const val COLLECTION_SUBJECTS = "subjects"
        const val COLLECTION_ASSIGNMENTS = "assignments"
        const val COLLECTION_NOTICES = "notices"
        const val COLLECTION_SETTINGS = "settings"
        const val DOC_SCHOOL_INFO = "school_info"
    }

    private val auth: FirebaseAuth by lazy { FirebaseProvider.auth }
    private val firestore: FirebaseFirestore by lazy { FirebaseProvider.firestore }
    private val authRepo: FirebaseAuthenticationRepository by lazy {
        FirebaseAuthenticationRepository(auth, firestore)
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Current authenticated user document state
    private val _currentUserDoc = MutableStateFlow<User?>(null)
    val currentUserDoc: StateFlow<User?> = _currentUserDoc.asStateFlow()

    // Local cached fallbacks for high reliability & immediate responsiveness
    private val _teachersFlow = MutableStateFlow<List<Teacher>>(emptyList())
    private val _studentsFlow = MutableStateFlow<List<Student>>(emptyList())
    private val _classesFlow = MutableStateFlow<List<SchoolClass>>(emptyList())
    private val _subjectsFlow = MutableStateFlow<List<Subject>>(emptyList())
    private val _assignmentsFlow = MutableStateFlow<List<Assignment>>(emptyList())
    private val _noticesFlow = MutableStateFlow<List<Notice>>(emptyList())
    private val _settingsFlow = MutableStateFlow(SchoolSettings())

    init {
        FirebaseProvider.init(context)
        repositoryScope.launch {
            seedInitialDemoData()
            setupAuthListener()
        }
    }

    private fun setupAuthListener() {
        if (!FirebaseProvider.isRealFirebaseConfigured) {
            // When real cloud Firebase is not configured, preserve local active session
            return
        }
        try {
            auth.addAuthStateListener { firebaseAuth ->
                val fbUser = firebaseAuth.currentUser
                if (fbUser != null) {
                    repositoryScope.launch {
                        fetchUserDocument(fbUser.uid, fbUser.email)
                    }
                } else {
                    _currentUserDoc.value = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting up auth listener: ${e.message}")
        }
    }

    private suspend fun fetchUserDocument(uid: String, email: String?) {
        try {
            val docSnap = firestore.collection(COLLECTION_USERS).document(uid).get().await()
            if (docSnap.exists()) {
                val user = docSnap.toObject(User::class.java)
                _currentUserDoc.value = user
            } else {
                val roleStr = when {
                    email?.contains("admin", ignoreCase = true) == true -> UserRole.ADMIN.name
                    email?.contains("teacher", ignoreCase = true) == true -> UserRole.TEACHER.name
                    email?.contains("parent", ignoreCase = true) == true -> UserRole.PARENT.name
                    email?.contains("staff", ignoreCase = true) == true -> UserRole.STAFF.name
                    else -> UserRole.STUDENT.name
                }
                val displayName = email?.substringBefore("@")?.replace(".", " ")?.capitalize() ?: "User"
                val newUser = User(
                    uid = uid,
                    email = email ?: "",
                    displayName = displayName,
                    role = roleStr,
                    schoolId = SCHOOL_ID,
                    active = true,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                firestore.collection(COLLECTION_USERS).document(uid).set(newUser).await()
                _currentUserDoc.value = newUser
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetching user document fallback: ${e.message}")
            val roleStr = if (email?.contains("admin", ignoreCase = true) == true) UserRole.ADMIN.name else UserRole.TEACHER.name
            val fallback = User(
                uid = uid,
                email = email ?: "staff@discoveryprimary.co.za",
                displayName = if (roleStr == UserRole.ADMIN.name) "Principal Raymond Peters" else "Mrs. Nomvula Khumalo",
                role = roleStr,
                schoolId = SCHOOL_ID,
                active = true,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            _currentUserDoc.value = fallback
        }
    }

    // ==========================================
    // AUTHENTICATION
    // ==========================================

    suspend fun signIn(email: String, pass: String): Result<User> = withContext(Dispatchers.IO) {
        val result = authRepo.signInWithEmail(email, pass)
        result.onSuccess { user ->
            _currentUserDoc.value = user
        }
        result
    }

    suspend fun signUp(email: String, pass: String, displayName: String, role: String): Result<User> = withContext(Dispatchers.IO) {
        val result = authRepo.registerWithEmail(email, pass, displayName, role, SCHOOL_ID)
        result.onSuccess { user ->
            _currentUserDoc.value = user
        }
        result
    }

    suspend fun verifyPhoneOtp(verificationId: String, code: String): Result<User> = withContext(Dispatchers.IO) {
        val result = authRepo.verifyPhoneCode(verificationId, code)
        result.onSuccess { user ->
            _currentUserDoc.value = user
        }
        result
    }

    fun signOut() {
        repositoryScope.launch {
            authRepo.signOut()
        }
        _currentUserDoc.value = null
    }

    fun setCurrentUser(user: User?) {
        _currentUserDoc.value = user
    }

    // ==========================================
    // TEACHERS CRUD
    // ==========================================

    fun getTeachersFlow(): Flow<List<Teacher>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = firestore.collection(COLLECTION_TEACHERS)
                .whereEqualTo("schoolId", SCHOOL_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_teachersFlow.value)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.toObjects(Teacher::class.java)
                        _teachersFlow.value = list
                        trySend(list)
                    } else {
                        trySend(_teachersFlow.value)
                    }
                }
        } catch (e: Exception) {
            trySend(_teachersFlow.value)
        }
        awaitClose { listener?.remove() }
    }

    suspend fun saveTeacher(teacher: Teacher): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = if (teacher.uid.isBlank()) UUID.randomUUID().toString() else teacher.uid
        val item = teacher.copy(
            uid = uid,
            schoolId = SCHOOL_ID,
            updatedAt = Timestamp.now()
        )
        try {
            firestore.collection(COLLECTION_TEACHERS).document(uid).set(item).await()
        } catch (e: Exception) {
            Log.w(TAG, "Save teacher firestore: ${e.message}")
        }
        val current = _teachersFlow.value.toMutableList()
        val index = current.indexOfFirst { it.uid == uid }
        if (index >= 0) current[index] = item else current.add(0, item)
        _teachersFlow.value = current
        Result.success(Unit)
    }

    suspend fun deleteTeacher(teacherUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_TEACHERS).document(teacherUid).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete teacher firestore: ${e.message}")
        }
        _teachersFlow.value = _teachersFlow.value.filter { it.uid != teacherUid }
        Result.success(Unit)
    }

    // ==========================================
    // STUDENTS CRUD
    // ==========================================

    fun getStudentsFlow(): Flow<List<Student>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = firestore.collection(COLLECTION_STUDENTS)
                .whereEqualTo("schoolId", SCHOOL_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_studentsFlow.value)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.toObjects(Student::class.java)
                        _studentsFlow.value = list
                        trySend(list)
                    } else {
                        trySend(_studentsFlow.value)
                    }
                }
        } catch (e: Exception) {
            trySend(_studentsFlow.value)
        }
        awaitClose { listener?.remove() }
    }

    suspend fun saveStudent(student: Student): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = if (student.uid.isBlank()) UUID.randomUUID().toString() else student.uid
        val item = student.copy(
            uid = uid,
            schoolId = SCHOOL_ID,
            updatedAt = Timestamp.now()
        )
        try {
            firestore.collection(COLLECTION_STUDENTS).document(uid).set(item).await()
        } catch (e: Exception) {
            Log.w(TAG, "Save student firestore: ${e.message}")
        }
        val current = _studentsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.uid == uid }
        if (index >= 0) current[index] = item else current.add(0, item)
        _studentsFlow.value = current
        Result.success(Unit)
    }

    suspend fun deleteStudent(studentUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_STUDENTS).document(studentUid).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete student firestore: ${e.message}")
        }
        _studentsFlow.value = _studentsFlow.value.filter { it.uid != studentUid }
        Result.success(Unit)
    }

    // ==========================================
    // CLASSES CRUD
    // ==========================================

    fun getClassesFlow(): Flow<List<SchoolClass>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = firestore.collection(COLLECTION_CLASSES)
                .whereEqualTo("schoolId", SCHOOL_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_classesFlow.value)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.toObjects(SchoolClass::class.java)
                        _classesFlow.value = list
                        trySend(list)
                    } else {
                        trySend(_classesFlow.value)
                    }
                }
        } catch (e: Exception) {
            trySend(_classesFlow.value)
        }
        awaitClose { listener?.remove() }
    }

    suspend fun saveClass(schoolClass: SchoolClass): Result<Unit> = withContext(Dispatchers.IO) {
        val classId = if (schoolClass.id.isBlank()) UUID.randomUUID().toString() else schoolClass.id
        val item = schoolClass.copy(
            id = classId,
            schoolId = SCHOOL_ID,
            updatedAt = Timestamp.now()
        )
        try {
            firestore.collection(COLLECTION_CLASSES).document(classId).set(item).await()
        } catch (e: Exception) {
            Log.w(TAG, "Save class firestore: ${e.message}")
        }
        val current = _classesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == classId }
        if (index >= 0) current[index] = item else current.add(item)
        _classesFlow.value = current
        Result.success(Unit)
    }

    suspend fun deleteClass(classId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_CLASSES).document(classId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete class firestore: ${e.message}")
        }
        _classesFlow.value = _classesFlow.value.filter { it.id != classId }
        Result.success(Unit)
    }

    // ==========================================
    // SUBJECTS CRUD
    // ==========================================

    fun getSubjectsFlow(): Flow<List<Subject>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = firestore.collection(COLLECTION_SUBJECTS)
                .whereEqualTo("schoolId", SCHOOL_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_subjectsFlow.value)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.toObjects(Subject::class.java)
                        _subjectsFlow.value = list
                        trySend(list)
                    } else {
                        trySend(_subjectsFlow.value)
                    }
                }
        } catch (e: Exception) {
            trySend(_subjectsFlow.value)
        }
        awaitClose { listener?.remove() }
    }

    suspend fun saveSubject(subject: Subject): Result<Unit> = withContext(Dispatchers.IO) {
        val subjectId = if (subject.id.isBlank()) UUID.randomUUID().toString() else subject.id
        val item = subject.copy(
            id = subjectId,
            schoolId = SCHOOL_ID,
            updatedAt = Timestamp.now()
        )
        try {
            firestore.collection(COLLECTION_SUBJECTS).document(subjectId).set(item).await()
        } catch (e: Exception) {
            Log.w(TAG, "Save subject firestore: ${e.message}")
        }
        val current = _subjectsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == subjectId }
        if (index >= 0) current[index] = item else current.add(item)
        _subjectsFlow.value = current
        Result.success(Unit)
    }

    suspend fun deleteSubject(subjectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_SUBJECTS).document(subjectId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete subject firestore: ${e.message}")
        }
        _subjectsFlow.value = _subjectsFlow.value.filter { it.id != subjectId }
        Result.success(Unit)
    }

    // ==========================================
    // ASSIGNMENTS CRUD
    // ==========================================

    fun getAssignmentsFlow(): Flow<List<Assignment>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = firestore.collection(COLLECTION_ASSIGNMENTS)
                .whereEqualTo("schoolId", SCHOOL_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_assignmentsFlow.value)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.toObjects(Assignment::class.java)
                        _assignmentsFlow.value = list
                        trySend(list)
                    } else {
                        trySend(_assignmentsFlow.value)
                    }
                }
        } catch (e: Exception) {
            trySend(_assignmentsFlow.value)
        }
        awaitClose { listener?.remove() }
    }

    suspend fun saveAssignment(assignment: Assignment): Result<Unit> = withContext(Dispatchers.IO) {
        val assignmentId = if (assignment.id.isBlank()) UUID.randomUUID().toString() else assignment.id
        val item = assignment.copy(
            id = assignmentId,
            schoolId = SCHOOL_ID,
            updatedAt = Timestamp.now()
        )
        try {
            firestore.collection(COLLECTION_ASSIGNMENTS).document(assignmentId).set(item).await()
        } catch (e: Exception) {
            Log.w(TAG, "Save assignment firestore: ${e.message}")
        }
        val current = _assignmentsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == assignmentId }
        if (index >= 0) current[index] = item else current.add(0, item)
        _assignmentsFlow.value = current
        Result.success(Unit)
    }

    suspend fun deleteAssignment(assignmentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_ASSIGNMENTS).document(assignmentId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete assignment firestore: ${e.message}")
        }
        _assignmentsFlow.value = _assignmentsFlow.value.filter { it.id != assignmentId }
        Result.success(Unit)
    }

    // ==========================================
    // NOTICES CRUD
    // ==========================================

    fun getNoticesFlow(): Flow<List<Notice>> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = firestore.collection(COLLECTION_NOTICES)
                .whereEqualTo("schoolId", SCHOOL_ID)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_noticesFlow.value)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.toObjects(Notice::class.java)
                        _noticesFlow.value = list
                        trySend(list)
                    } else {
                        trySend(_noticesFlow.value)
                    }
                }
        } catch (e: Exception) {
            trySend(_noticesFlow.value)
        }
        awaitClose { listener?.remove() }
    }

    suspend fun saveNotice(notice: Notice): Result<Unit> = withContext(Dispatchers.IO) {
        val noticeId = if (notice.id.isBlank()) UUID.randomUUID().toString() else notice.id
        val item = notice.copy(
            id = noticeId,
            schoolId = SCHOOL_ID,
            updatedAt = Timestamp.now()
        )
        try {
            firestore.collection(COLLECTION_NOTICES).document(noticeId).set(item).await()
        } catch (e: Exception) {
            Log.w(TAG, "Save notice firestore: ${e.message}")
        }
        val current = _noticesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == noticeId }
        if (index >= 0) current[index] = item else current.add(0, item)
        _noticesFlow.value = current
        Result.success(Unit)
    }

    suspend fun deleteNotice(noticeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_NOTICES).document(noticeId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete notice firestore: ${e.message}")
        }
        _noticesFlow.value = _noticesFlow.value.filter { it.id != noticeId }
        Result.success(Unit)
    }

    // ==========================================
    // SETTINGS
    // ==========================================

    fun getSettingsFlow(): Flow<SchoolSettings> = callbackFlow {
        var listener: ListenerRegistration? = null
        try {
            listener = firestore.collection(COLLECTION_SETTINGS).document(DOC_SCHOOL_INFO)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_settingsFlow.value)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val s = snapshot.toObject(SchoolSettings::class.java) ?: _settingsFlow.value
                        _settingsFlow.value = s
                        trySend(s)
                    } else {
                        trySend(_settingsFlow.value)
                    }
                }
        } catch (e: Exception) {
            trySend(_settingsFlow.value)
        }
        awaitClose { listener?.remove() }
    }

    suspend fun saveSettings(settings: SchoolSettings): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection(COLLECTION_SETTINGS).document(DOC_SCHOOL_INFO).set(settings).await()
        } catch (e: Exception) {
            Log.w(TAG, "Save settings firestore: ${e.message}")
        }
        _settingsFlow.value = settings
        Result.success(Unit)
    }

    // ==========================================
    // INITIAL SEED DATA (DISCOVERY PRIMARY SCHOOL)
    // ==========================================

    private suspend fun seedInitialDemoData() {
        val initialClasses = listOf(
            SchoolClass("class_4a", SCHOOL_ID, "Grade 4A", "Grade 4", listOf("teach_khumalo"), listOf("subj_eng", "subj_nst")),
            SchoolClass("class_4b", SCHOOL_ID, "Grade 4B", "Grade 4", listOf("teach_sithole"), listOf("subj_math")),
            SchoolClass("class_5a", SCHOOL_ID, "Grade 5A", "Grade 5", listOf("teach_pillay"), listOf("subj_robotics")),
            SchoolClass("class_7a", SCHOOL_ID, "Grade 7A", "Grade 7", listOf("teach_adams"), listOf("subj_soc"))
        )
        _classesFlow.value = initialClasses

        val initialSubjects = listOf(
            Subject("subj_math", SCHOOL_ID, "Mathematics", "MATH-CAPS-4", listOf("teach_sithole")),
            Subject("subj_eng", SCHOOL_ID, "English Home Language", "ENG-CAPS-4", listOf("teach_khumalo")),
            Subject("subj_nst", SCHOOL_ID, "Natural Sciences & Tech", "NST-CAPS-4", listOf("teach_khumalo")),
            Subject("subj_robotics", SCHOOL_ID, "Coding & Robotics", "COD-CAPS-4", listOf("teach_pillay")),
            Subject("subj_soc", SCHOOL_ID, "Social Sciences", "SOC-CAPS-4", listOf("teach_adams"))
        )
        _subjectsFlow.value = initialSubjects

        val initialTeachers = listOf(
            Teacher(
                uid = "teach_khumalo",
                userId = "teacher_uid_khumalo",
                schoolId = SCHOOL_ID,
                employeeNumber = "DPS-EMP-102",
                firstName = "Nomvula",
                lastName = "Khumalo",
                email = "teacher.khumalo@discoveryprimary.co.za",
                phoneNumber = "+27825554102",
                classIds = listOf("class_4a"),
                subjectIds = listOf("subj_eng", "subj_nst")
            ),
            Teacher(
                uid = "teach_sithole",
                userId = "teacher_uid_sithole",
                schoolId = SCHOOL_ID,
                employeeNumber = "DPS-EMP-104",
                firstName = "Thabo",
                lastName = "Sithole",
                email = "teacher.sithole@discoveryprimary.co.za",
                phoneNumber = "+27834448921",
                classIds = listOf("class_4b"),
                subjectIds = listOf("subj_math")
            ),
            Teacher(
                uid = "teach_pillay",
                userId = "teacher_uid_pillay",
                schoolId = SCHOOL_ID,
                employeeNumber = "DPS-EMP-108",
                firstName = "Kavish",
                lastName = "Pillay",
                email = "k.pillay@discoveryprimary.co.za",
                phoneNumber = "+27712223411",
                classIds = listOf("class_5a"),
                subjectIds = listOf("subj_robotics")
            )
        )
        _teachersFlow.value = initialTeachers

        val initialStudents = listOf(
            Student("stu_1", "stu_user_1", SCHOOL_ID, "DPS-4012", "Siyabonga", "Dlamini", "siyabonga.d@discoveryprimary.co.za", "+27821234567", "class_4a", listOf("parent_dlamini")),
            Student("stu_2", "stu_user_2", SCHOOL_ID, "DPS-4015", "Amahle", "Ndlovu", "amahle.n@discoveryprimary.co.za", "+27832345678", "class_4a", listOf("parent_ndlovu")),
            Student("stu_3", "stu_user_3", SCHOOL_ID, "DPS-4022", "Liam", "Van Der Merwe", "liam.vdm@discoveryprimary.co.za", "+27843456789", "class_4a", listOf("parent_vdm")),
            Student("stu_4", "stu_user_4", SCHOOL_ID, "DPS-4031", "Fatima", "Patel", "fatima.p@discoveryprimary.co.za", "+27824567890", "class_4a", listOf("parent_patel")),
            Student("stu_5", "stu_user_5", SCHOOL_ID, "DPS-4045", "Kagiso", "Mokoena", "kagiso.m@discoveryprimary.co.za", "+27795678901", "class_4a", listOf("parent_mokoena")),
            Student("stu_6", "stu_user_6", SCHOOL_ID, "DPS-4050", "Chloe", "Smith", "chloe.s@discoveryprimary.co.za", "+27836789012", "class_4b", listOf("parent_smith"))
        )
        _studentsFlow.value = initialStudents

        val initialAssignments = listOf(
            Assignment(
                id = "ass_1",
                schoolId = SCHOOL_ID,
                classId = "class_4a",
                subjectId = "subj_math",
                teacherId = "teach_sithole",
                title = "Fractions & Decimals Problem Set",
                description = "Complete pages 42-45 in the CAPS Mathematics workbook.",
                dueDate = Timestamp.now()
            ),
            Assignment(
                id = "ass_2",
                schoolId = SCHOOL_ID,
                classId = "class_4a",
                subjectId = "subj_nst",
                teacherId = "teach_khumalo",
                title = "Plant Cell Structure Model & Diagram",
                description = "Draw and label a plant cell showing cell wall, nucleus, and cytoplasm.",
                dueDate = Timestamp.now()
            ),
            Assignment(
                id = "ass_3",
                schoolId = SCHOOL_ID,
                classId = "class_4a",
                subjectId = "subj_eng",
                teacherId = "teach_khumalo",
                title = "Creative Writing: Mapungubwe Story",
                description = "Write a 150-word narrative essay describing life in early southern African kingdoms.",
                dueDate = Timestamp.now()
            )
        )
        _assignmentsFlow.value = initialAssignments

        val initialNotices = listOf(
            Notice(
                id = "not_1",
                schoolId = SCHOOL_ID,
                authorId = "admin_uid_demo",
                title = "Term 3 Examination Timetable Finalized",
                message = "The official GDE District D12 exam timetable has been posted. Assessments commence 15 October.",
                targetRole = "ALL",
                published = true,
                createdAt = Timestamp.now()
            ),
            Notice(
                id = "not_2",
                schoolId = SCHOOL_ID,
                authorId = "teach_khumalo",
                title = "Grade 4 Science Project Submissions Notice",
                message = "Reminder for Grade 4A learners: bring your Natural Science recycled materials tomorrow.",
                targetRole = "STUDENT",
                published = true,
                createdAt = Timestamp.now()
            ),
            Notice(
                id = "not_3",
                schoolId = SCHOOL_ID,
                authorId = "admin_uid_demo",
                title = "Severe Weather Protocol & Indoor Extramurals",
                message = "In light of thunder forecast in Roodepoort, extramural sports will be held in the school hall.",
                targetRole = "ALL",
                published = true,
                createdAt = Timestamp.now()
            )
        )
        _noticesFlow.value = initialNotices

        // Push to Firestore asynchronously
        try {
            initialClasses.forEach { firestore.collection(COLLECTION_CLASSES).document(it.id).set(it) }
            initialSubjects.forEach { firestore.collection(COLLECTION_SUBJECTS).document(it.id).set(it) }
            initialTeachers.forEach { firestore.collection(COLLECTION_TEACHERS).document(it.uid).set(it) }
            initialStudents.forEach { firestore.collection(COLLECTION_STUDENTS).document(it.uid).set(it) }
            initialAssignments.forEach { firestore.collection(COLLECTION_ASSIGNMENTS).document(it.id).set(it) }
            initialNotices.forEach { firestore.collection(COLLECTION_NOTICES).document(it.id).set(it) }
            firestore.collection(COLLECTION_SETTINGS).document(DOC_SCHOOL_INFO).set(SchoolSettings())
        } catch (e: Exception) {
            Log.w(TAG, "Initial seed to Firestore: ${e.message}")
        }
    }
}
