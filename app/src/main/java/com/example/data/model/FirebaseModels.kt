package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Supported User Roles in the Discovery Primary LMS.
 */
enum class UserRole {
    ADMIN,
    TEACHER,
    STUDENT,
    PARENT,
    STAFF;

    companion object {
        fun fromString(role: String?): UserRole? {
            return when (role?.trim()?.uppercase()) {
                "ADMIN" -> ADMIN
                "TEACHER" -> TEACHER
                "STUDENT" -> STUDENT
                "PARENT" -> PARENT
                "STAFF" -> STAFF
                else -> null
            }
        }
    }
}

/** Audiences a notice can target. "ALL" reaches every active member of the school. */
object NoticeAudience {
    const val ALL = "ALL"
    val adminChoices = listOf(ALL, UserRole.PARENT.name, UserRole.TEACHER.name, UserRole.STUDENT.name, UserRole.STAFF.name)
    val teacherChoices = listOf(UserRole.PARENT.name, UserRole.STUDENT.name)
}

/**
 * Authenticated User Document in Firestore (/users/{uid}).
 * Only an administrator may change role, schoolId or active (enforced by firestore.rules).
 */
@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val role: String = UserRole.PARENT.name,
    val schoolId: String = SCHOOL_ID,
    val active: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val userRole: UserRole?
        get() = UserRole.fromString(role)

    @get:Exclude
    val label: String
        get() = displayName.ifBlank { email.ifBlank { phoneNumber.ifBlank { uid } } }
}

/**
 * Teacher Entity in Firestore (/teachers/{uid}). The document id is the teacher's
 * Firebase Auth uid; classIds is the authoritative list used by the security rules.
 */
@IgnoreExtraProperties
data class Teacher(
    val uid: String = "",
    val userId: String = "",
    val schoolId: String = SCHOOL_ID,
    val employeeNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val classIds: List<String> = emptyList(),
    val subjectIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val name: String
        get() = "$firstName $lastName".trim().ifBlank { email.ifBlank { "Unnamed teacher" } }
}

/**
 * Student Entity in Firestore (/students/{studentId}).
 * userId links the learner's own login (optional); parentIds lists the linked parent uids.
 */
@IgnoreExtraProperties
data class Student(
    val uid: String = "",
    val userId: String = "",
    val schoolId: String = SCHOOL_ID,
    val studentNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val classId: String = "",
    val parentIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val fullName: String
        get() = "$firstName $lastName".trim().ifBlank { studentNumber.ifBlank { "Unnamed learner" } }
}

/**
 * School Class Entity in Firestore (/classes/{classId})
 */
@IgnoreExtraProperties
data class SchoolClass(
    val id: String = "",
    val schoolId: String = SCHOOL_ID,
    val name: String = "",
    val grade: String = "",
    val teacherIds: List<String> = emptyList(),
    val subjectIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * Subject Entity in Firestore (/subjects/{subjectId})
 */
@IgnoreExtraProperties
data class Subject(
    val id: String = "",
    val schoolId: String = SCHOOL_ID,
    val name: String = "",
    val code: String = "",
    val teacherIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * Assignment Entity in Firestore (/assignments/{assignmentId})
 */
@IgnoreExtraProperties
data class Assignment(
    val id: String = "",
    val schoolId: String = SCHOOL_ID,
    val classId: String = "",
    val subjectId: String = "",
    val teacherId: String = "",
    val title: String = "",
    val description: String = "",
    val dueDate: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * School Notice Entity in Firestore (/notices/{noticeId}).
 * targetRole is a [UserRole] name or "ALL"; a non-blank classId narrows it to one class.
 */
@IgnoreExtraProperties
data class Notice(
    val id: String = "",
    val schoolId: String = SCHOOL_ID,
    val authorId: String = "",
    val title: String = "",
    val message: String = "",
    val targetRole: String = NoticeAudience.ALL,
    val classId: String = "",
    val published: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * Essential School Settings (/settings/school_info)
 */
@IgnoreExtraProperties
data class SchoolSettings(
    val schoolName: String = "Discovery Primary School",
    val emisNumber: String = "",
    val district: String = "",
    val province: String = "",
    val principalName: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val academicTerm: String = "",
    val schoolId: String = SCHOOL_ID
)

const val SCHOOL_ID = "discovery-primary"
