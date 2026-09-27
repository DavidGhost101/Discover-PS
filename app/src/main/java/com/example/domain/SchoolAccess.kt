package com.example.domain

import com.example.data.model.Assignment
import com.example.data.model.Notice
import com.example.data.model.NoticeAudience
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Teacher
import com.example.data.model.UserRole
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date

/**
 * Pure view-model logic deciding what each role is shown. The Firestore rules are the
 * security boundary; these functions decide relevance (e.g. which class notices apply
 * to a parent's children) and are unit tested without Android or Firebase.
 */
object SchoolAccess {

    /** Notices a member should see, newest first. */
    fun visibleNotices(
        notices: List<Notice>,
        role: UserRole,
        uid: String,
        classIds: Set<String>
    ): List<Notice> {
        if (role == UserRole.ADMIN) return notices.sortedByDescending { it.createdAt }
        return notices.filter { notice ->
            val authoredByMe = notice.authorId == uid
            val audienceMatches = notice.published &&
                (notice.targetRole == NoticeAudience.ALL || notice.targetRole == role.name)
            val classMatches = notice.classId.isBlank() || notice.classId in classIds
            authoredByMe || (audienceMatches && classMatches)
        }.sortedByDescending { it.createdAt }
    }

    /** Assignments for the given classes, soonest due first (undated last). */
    fun assignmentsForClasses(assignments: List<Assignment>, classIds: Set<String>): List<Assignment> =
        assignments.filter { it.classId in classIds }
            .sortedWith(compareBy<Assignment, Timestamp?>(nullsLast()) { it.dueDate })

    /** Classes a teacher is assigned to (teacher.classIds is authoritative). */
    fun teacherClasses(teacher: Teacher?, classes: List<SchoolClass>): List<SchoolClass> {
        if (teacher == null) return emptyList()
        val ids = teacher.classIds.toSet()
        return classes.filter { it.id in ids }.sortedBy { it.name }
    }

    /** Children linked to a parent account. */
    fun childrenOf(parentUid: String, students: List<Student>): List<Student> =
        students.filter { parentUid in it.parentIds }.sortedBy { it.fullName }

    /** Teachers teaching a class. */
    fun teachersOfClass(classId: String, teachers: List<Teacher>): List<Teacher> =
        teachers.filter { classId in it.classIds }.sortedBy { it.name }

    enum class DueStatus(val label: String) {
        NO_DUE_DATE("No due date"),
        UPCOMING("Upcoming"),
        DUE_TODAY("Due today"),
        OVERDUE("Past due")
    }

    fun dueStatus(dueDate: Date?, now: Date = Date()): DueStatus {
        if (dueDate == null) return DueStatus.NO_DUE_DATE
        val due = Calendar.getInstance().apply { time = dueDate }
        val today = Calendar.getInstance().apply { time = now }
        val sameDay = due.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            due.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        return when {
            sameDay -> DueStatus.DUE_TODAY
            dueDate.before(now) -> DueStatus.OVERDUE
            else -> DueStatus.UPCOMING
        }
    }

    /** Items added and removed between two relationship lists (for keeping links in sync). */
    fun diff(before: Collection<String>, after: Collection<String>): Pair<Set<String>, Set<String>> {
        val b = before.filter { it.isNotBlank() }.toSet()
        val a = after.filter { it.isNotBlank() }.toSet()
        return (a - b) to (b - a)
    }
}

/** Input validation shared by the forms. Returns an error message or null when valid. */
object FormValidation {
    private val EMAIL = Regex("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MIN_PASSWORD_LENGTH = 8

    fun email(value: String): String? = when {
        value.isBlank() -> "Email address is required."
        !EMAIL.matches(value.trim()) -> "Enter a valid email address."
        else -> null
    }

    fun password(value: String): String? = when {
        value.isEmpty() -> "Password is required."
        value.length < MIN_PASSWORD_LENGTH -> "Password must contain at least $MIN_PASSWORD_LENGTH characters."
        value.none { it.isDigit() } || value.none { it.isLetter() } -> "Password must contain letters and numbers."
        else -> null
    }

    fun required(value: String, field: String): String? =
        if (value.isBlank()) "$field is required." else null

    /** Optional phone number: blank or 9-15 digits with an optional leading +. */
    fun phone(value: String): String? {
        if (value.isBlank()) return null
        val cleaned = value.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        return if (Regex("^\\+?[0-9]{9,15}$").matches(cleaned)) null else "Enter a valid phone number."
    }

    /** Roles an administrator may assign. */
    val assignableRoles: List<UserRole> = UserRole.entries.toList()

    /** Self-registration is always a parent account awaiting approval. */
    val selfRegistrationRole: UserRole = UserRole.PARENT
}
