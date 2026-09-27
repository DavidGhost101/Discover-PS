package com.example.data.repository

import com.example.data.firebase.FirebaseProvider
import com.example.data.friendlyError
import com.example.data.model.Assignment
import com.example.data.model.Notice
import com.example.data.model.NoticeAudience
import com.example.data.model.SCHOOL_ID
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolSettings
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.Teacher
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.domain.FormValidation
import com.example.domain.SchoolAccess
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** A live collection: items plus loading / error state (never fake fallback data). */
data class Remote<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
) {
    companion object {
        fun <T> idle(): Remote<T> = Remote(loading = false)
    }
}

class FirebaseSchoolRepository {
    companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_TEACHERS = "teachers"
        const val COLLECTION_STUDENTS = "students"
        const val COLLECTION_CLASSES = "classes"
        const val COLLECTION_SUBJECTS = "subjects"
        const val COLLECTION_ASSIGNMENTS = "assignments"
        const val COLLECTION_NOTICES = "notices"
        const val COLLECTION_SETTINGS = "settings"
        const val DOC_SCHOOL_INFO = "school_info"
        private const val MAX_IN_QUERY = 30
    }

    private val db: FirebaseFirestore by lazy { FirebaseProvider.firestore }

    private fun col(name: String) = db.collection(name)
    private fun school(name: String): Query = col(name).whereEqualTo("schoolId", SCHOOL_ID)

    // ==========================================
    // LISTENERS (each flow owns exactly one snapshot listener, removed on cancel)
    // ==========================================

    private fun <T : Any> listen(query: Query, type: Class<T>): Flow<Remote<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Remote(loading = false, error = friendlyError(error)))
            } else if (snapshot != null) {
                trySend(Remote(snapshot.toObjects(type), loading = false))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun <T : Any> listenDoc(ref: DocumentReference, type: Class<T>): Flow<Remote<T>> = callbackFlow {
        val registration = ref.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(Remote(loading = false, error = friendlyError(error)))
                snapshot == null || !snapshot.exists() -> trySend(Remote(emptyList(), loading = false))
                else -> trySend(Remote(listOfNotNull(snapshot.toObject(type)), loading = false))
            }
        }
        awaitClose { registration.remove() }
    }

    /** whereIn is limited to 30 values, so larger lists are split and merged. */
    private fun <T : Any> listenIn(base: Query, field: String, values: List<String>, type: Class<T>): Flow<Remote<T>> {
        val distinct = values.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return flowOf(Remote.idle())
        val chunks = distinct.chunked(MAX_IN_QUERY)
        return callbackFlow {
            // Snapshot callbacks arrive on the main thread, so this list is not shared across threads.
            val parts = MutableList<Remote<T>>(chunks.size) { Remote() }
            fun emitMerged() {
                trySend(
                    Remote(
                        items = parts.flatMap { it.items },
                        loading = parts.any { it.loading },
                        error = parts.firstNotNullOfOrNull { it.error }
                    )
                )
            }
            val registrations = chunks.mapIndexed { index, chunk ->
                base.whereIn(field, chunk).addSnapshotListener { snapshot, error ->
                    parts[index] = when {
                        error != null -> Remote(loading = false, error = friendlyError(error))
                        snapshot != null -> Remote(snapshot.toObjects(type), loading = false)
                        else -> return@addSnapshotListener
                    }
                    emitMerged()
                }
            }
            awaitClose { registrations.forEach { it.remove() } }
        }
    }

    fun allUsers(): Flow<Remote<User>> = listen(school(COLLECTION_USERS), User::class.java)
    fun teachers(): Flow<Remote<Teacher>> = listen(school(COLLECTION_TEACHERS), Teacher::class.java)
    fun teacher(uid: String): Flow<Remote<Teacher>> = listenDoc(col(COLLECTION_TEACHERS).document(uid), Teacher::class.java)
    fun allStudents(): Flow<Remote<Student>> = listen(school(COLLECTION_STUDENTS), Student::class.java)
    fun studentsInClasses(classIds: List<String>): Flow<Remote<Student>> =
        listenIn(school(COLLECTION_STUDENTS), "classId", classIds, Student::class.java)
    fun childrenOf(parentUid: String): Flow<Remote<Student>> =
        listen(col(COLLECTION_STUDENTS).whereArrayContains("parentIds", parentUid), Student::class.java)
    fun studentRecordsOf(studentUid: String): Flow<Remote<Student>> =
        listen(col(COLLECTION_STUDENTS).whereEqualTo("userId", studentUid), Student::class.java)
    fun classes(): Flow<Remote<SchoolClass>> = listen(school(COLLECTION_CLASSES), SchoolClass::class.java)
    fun subjects(): Flow<Remote<Subject>> = listen(school(COLLECTION_SUBJECTS), Subject::class.java)
    fun assignments(): Flow<Remote<Assignment>> = listen(school(COLLECTION_ASSIGNMENTS), Assignment::class.java)
    fun allNotices(): Flow<Remote<Notice>> = listen(school(COLLECTION_NOTICES), Notice::class.java)

    /** Published notices addressed to everyone or to [role]. */
    fun noticesFor(role: UserRole): Flow<Remote<Notice>> = listen(
        school(COLLECTION_NOTICES)
            .whereEqualTo("published", true)
            .whereIn("targetRole", listOf(NoticeAudience.ALL, role.name)),
        Notice::class.java
    )

    fun noticesAuthoredBy(uid: String): Flow<Remote<Notice>> =
        listen(school(COLLECTION_NOTICES).whereEqualTo("authorId", uid), Notice::class.java)

    fun settings(): Flow<Remote<SchoolSettings>> =
        listenDoc(col(COLLECTION_SETTINGS).document(DOC_SCHOOL_INFO), SchoolSettings::class.java)

    // ==========================================
    // USERS & PROFILES
    // ==========================================

    /** Admin edit of another account (role / status / contact details). */
    suspend fun updateUser(user: User): Result<Unit> = write {
        UserRole.fromString(user.role) ?: throw IllegalArgumentException("Choose a valid role.")
        FormValidation.required(user.displayName, "Name")?.let { throw IllegalArgumentException(it) }
        FormValidation.phone(user.phoneNumber)?.let { throw IllegalArgumentException(it) }
        col(COLLECTION_USERS).document(user.uid).set(user.copy(schoolId = SCHOOL_ID, updatedAt = Timestamp.now())).await()
    }

    /**
     * Administrator change to another account through the server (Cloud Function). Used for
     * the protected Owner: the server refuses it with 403 and records the attempt.
     */
    suspend fun adminUpdateUserOnServer(user: User): Result<Unit> = write {
        FirebaseFunctions.getInstance().getHttpsCallable("adminUpdateUser").call(
            mapOf(
                "uid" to user.uid,
                "role" to user.role,
                "active" to user.active,
                "displayName" to user.displayName.trim(),
                "phoneNumber" to user.phoneNumber.trim()
            )
        ).await()
    }

    /** Self-service profile edit: rules only allow these three fields to change. */
    suspend fun updateOwnProfile(uid: String, displayName: String, phoneNumber: String): Result<Unit> = write {
        FormValidation.required(displayName, "Name")?.let { throw IllegalArgumentException(it) }
        if (displayName.trim().length > 100) throw IllegalArgumentException("Name is too long.")
        FormValidation.phone(phoneNumber)?.let { throw IllegalArgumentException(it) }
        col(COLLECTION_USERS).document(uid).update(
            mapOf(
                "displayName" to displayName.trim(),
                "phoneNumber" to phoneNumber.trim(),
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    // ==========================================
    // TEACHERS (teacher.classIds <-> class.teacherIds, teacher.subjectIds <-> subject.teacherIds)
    // ==========================================

    suspend fun saveTeacher(teacher: Teacher, previous: Teacher?): Result<Unit> = write {
        require(teacher.uid.isNotBlank()) { "Teacher account is missing." }
        FormValidation.required(teacher.firstName, "First name")?.let { throw IllegalArgumentException(it) }
        FormValidation.phone(teacher.phoneNumber)?.let { throw IllegalArgumentException(it) }
        val now = Timestamp.now()
        val batch = db.batch()
        batch.set(
            col(COLLECTION_TEACHERS).document(teacher.uid),
            teacher.copy(
                userId = teacher.uid,
                schoolId = SCHOOL_ID,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now
            )
        )
        val (addedClasses, removedClasses) = SchoolAccess.diff(previous?.classIds.orEmpty(), teacher.classIds)
        addedClasses.forEach { batch.update(col(COLLECTION_CLASSES).document(it), "teacherIds", FieldValue.arrayUnion(teacher.uid)) }
        removedClasses.forEach { batch.update(col(COLLECTION_CLASSES).document(it), "teacherIds", FieldValue.arrayRemove(teacher.uid)) }
        val (addedSubjects, removedSubjects) = SchoolAccess.diff(previous?.subjectIds.orEmpty(), teacher.subjectIds)
        addedSubjects.forEach { batch.update(col(COLLECTION_SUBJECTS).document(it), "teacherIds", FieldValue.arrayUnion(teacher.uid)) }
        removedSubjects.forEach { batch.update(col(COLLECTION_SUBJECTS).document(it), "teacherIds", FieldValue.arrayRemove(teacher.uid)) }
        batch.commit().await()
    }

    /** Removes the teacher record and every class/subject link. The login itself is
     *  deactivated separately from the Users tab. */
    suspend fun deleteTeacher(teacher: Teacher): Result<Unit> = write {
        val batch = db.batch()
        batch.delete(col(COLLECTION_TEACHERS).document(teacher.uid))
        teacher.classIds.filter { it.isNotBlank() }.forEach {
            batch.update(col(COLLECTION_CLASSES).document(it), "teacherIds", FieldValue.arrayRemove(teacher.uid))
        }
        teacher.subjectIds.filter { it.isNotBlank() }.forEach {
            batch.update(col(COLLECTION_SUBJECTS).document(it), "teacherIds", FieldValue.arrayRemove(teacher.uid))
        }
        batch.commit().await()
    }

    // ==========================================
    // STUDENTS
    // ==========================================

    suspend fun saveStudent(student: Student): Result<Unit> = write {
        FormValidation.required(student.firstName, "First name")?.let { throw IllegalArgumentException(it) }
        FormValidation.required(student.classId, "Class")?.let { throw IllegalArgumentException(it) }
        if (student.email.isNotBlank()) FormValidation.email(student.email)?.let { throw IllegalArgumentException(it) }
        val id = student.uid.ifBlank { UUID.randomUUID().toString() }
        val now = Timestamp.now()
        col(COLLECTION_STUDENTS).document(id).set(
            student.copy(
                uid = id,
                schoolId = SCHOOL_ID,
                parentIds = student.parentIds.filter { it.isNotBlank() }.distinct(),
                createdAt = student.createdAt ?: now,
                updatedAt = now
            )
        ).await()
    }

    suspend fun deleteStudent(studentId: String): Result<Unit> = write {
        col(COLLECTION_STUDENTS).document(studentId).delete().await()
    }

    // ==========================================
    // CLASSES
    // ==========================================

    suspend fun saveClass(schoolClass: SchoolClass, previous: SchoolClass?): Result<Unit> = write {
        FormValidation.required(schoolClass.name, "Class name")?.let { throw IllegalArgumentException(it) }
        val id = schoolClass.id.ifBlank { UUID.randomUUID().toString() }
        val now = Timestamp.now()
        val batch = db.batch()
        batch.set(
            col(COLLECTION_CLASSES).document(id),
            schoolClass.copy(id = id, schoolId = SCHOOL_ID, createdAt = previous?.createdAt ?: now, updatedAt = now)
        )
        val (added, removed) = SchoolAccess.diff(previous?.teacherIds.orEmpty(), schoolClass.teacherIds)
        added.forEach { batch.update(col(COLLECTION_TEACHERS).document(it), "classIds", FieldValue.arrayUnion(id)) }
        removed.forEach { batch.update(col(COLLECTION_TEACHERS).document(it), "classIds", FieldValue.arrayRemove(id)) }
        batch.commit().await()
    }

    suspend fun deleteClass(schoolClass: SchoolClass, enrolledStudents: Int): Result<Unit> = write {
        if (enrolledStudents > 0) {
            throw IllegalStateException("Move the $enrolledStudents enrolled learner(s) to another class before deleting ${schoolClass.name}.")
        }
        val batch = db.batch()
        batch.delete(col(COLLECTION_CLASSES).document(schoolClass.id))
        schoolClass.teacherIds.filter { it.isNotBlank() }.forEach {
            batch.update(col(COLLECTION_TEACHERS).document(it), "classIds", FieldValue.arrayRemove(schoolClass.id))
        }
        batch.commit().await()
    }

    // ==========================================
    // SUBJECTS (subject.teacherIds <-> teacher.subjectIds, class.subjectIds)
    // ==========================================

    suspend fun saveSubject(
        subject: Subject,
        previous: Subject?,
        classIds: List<String>,
        previousClassIds: List<String>
    ): Result<Unit> = write {
        FormValidation.required(subject.name, "Subject name")?.let { throw IllegalArgumentException(it) }
        val id = subject.id.ifBlank { UUID.randomUUID().toString() }
        val now = Timestamp.now()
        val batch = db.batch()
        batch.set(
            col(COLLECTION_SUBJECTS).document(id),
            subject.copy(id = id, schoolId = SCHOOL_ID, createdAt = previous?.createdAt ?: now, updatedAt = now)
        )
        val (addedTeachers, removedTeachers) = SchoolAccess.diff(previous?.teacherIds.orEmpty(), subject.teacherIds)
        addedTeachers.forEach { batch.update(col(COLLECTION_TEACHERS).document(it), "subjectIds", FieldValue.arrayUnion(id)) }
        removedTeachers.forEach { batch.update(col(COLLECTION_TEACHERS).document(it), "subjectIds", FieldValue.arrayRemove(id)) }
        val (addedClasses, removedClasses) = SchoolAccess.diff(previousClassIds, classIds)
        addedClasses.forEach { batch.update(col(COLLECTION_CLASSES).document(it), "subjectIds", FieldValue.arrayUnion(id)) }
        removedClasses.forEach { batch.update(col(COLLECTION_CLASSES).document(it), "subjectIds", FieldValue.arrayRemove(id)) }
        batch.commit().await()
    }

    suspend fun deleteSubject(subject: Subject, classIds: List<String>): Result<Unit> = write {
        val batch = db.batch()
        batch.delete(col(COLLECTION_SUBJECTS).document(subject.id))
        subject.teacherIds.filter { it.isNotBlank() }.forEach {
            batch.update(col(COLLECTION_TEACHERS).document(it), "subjectIds", FieldValue.arrayRemove(subject.id))
        }
        classIds.filter { it.isNotBlank() }.forEach {
            batch.update(col(COLLECTION_CLASSES).document(it), "subjectIds", FieldValue.arrayRemove(subject.id))
        }
        batch.commit().await()
    }

    // ==========================================
    // ASSIGNMENTS
    // ==========================================

    suspend fun saveAssignment(assignment: Assignment): Result<Unit> = write {
        FormValidation.required(assignment.title, "Title")?.let { throw IllegalArgumentException(it) }
        FormValidation.required(assignment.classId, "Class")?.let { throw IllegalArgumentException(it) }
        if (assignment.title.trim().length > 200) throw IllegalArgumentException("Title is too long.")
        val id = assignment.id.ifBlank { UUID.randomUUID().toString() }
        val now = Timestamp.now()
        col(COLLECTION_ASSIGNMENTS).document(id).set(
            assignment.copy(
                id = id,
                title = assignment.title.trim(),
                description = assignment.description.trim(),
                schoolId = SCHOOL_ID,
                createdAt = assignment.createdAt ?: now,
                updatedAt = now
            )
        ).await()
    }

    suspend fun deleteAssignment(assignmentId: String): Result<Unit> = write {
        col(COLLECTION_ASSIGNMENTS).document(assignmentId).delete().await()
    }

    // ==========================================
    // NOTICES
    // ==========================================

    suspend fun saveNotice(notice: Notice): Result<Unit> = write {
        FormValidation.required(notice.title, "Title")?.let { throw IllegalArgumentException(it) }
        FormValidation.required(notice.message, "Message")?.let { throw IllegalArgumentException(it) }
        if (notice.title.trim().length > 200) throw IllegalArgumentException("Title is too long.")
        val id = notice.id.ifBlank { UUID.randomUUID().toString() }
        val now = Timestamp.now()
        col(COLLECTION_NOTICES).document(id).set(
            notice.copy(
                id = id,
                title = notice.title.trim(),
                message = notice.message.trim(),
                schoolId = SCHOOL_ID,
                createdAt = notice.createdAt ?: now,
                updatedAt = now
            )
        ).await()
    }

    suspend fun deleteNotice(noticeId: String): Result<Unit> = write {
        col(COLLECTION_NOTICES).document(noticeId).delete().await()
    }

    // ==========================================
    // SETTINGS
    // ==========================================

    suspend fun saveSettings(settings: SchoolSettings): Result<Unit> = write {
        FormValidation.required(settings.schoolName, "School name")?.let { throw IllegalArgumentException(it) }
        if (settings.contactEmail.isNotBlank()) FormValidation.email(settings.contactEmail)?.let { throw IllegalArgumentException(it) }
        FormValidation.phone(settings.contactPhone)?.let { throw IllegalArgumentException(it) }
        col(COLLECTION_SETTINGS).document(DOC_SCHOOL_INFO).set(settings.copy(schoolId = SCHOOL_ID)).await()
    }

    /** Runs a write and converts any failure (validation, permission, network) into a
     *  user-facing message instead of silently reporting success. */
    private suspend fun write(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(Exception(friendlyError(e), e))
    }
}
