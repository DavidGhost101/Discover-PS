package com.example.data.model

import com.google.firebase.Timestamp
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
            return when (role?.uppercase()) {
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

/**
 * Authenticated User Document in Firestore (/users/{uid})
 */
@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val role: String = UserRole.STUDENT.name,
    val schoolId: String = "discovery-primary",
    val active: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val userRole: UserRole?
        get() = UserRole.fromString(role)
}

/**
 * Teacher Entity in Firestore (/teachers/{uid})
 */
@IgnoreExtraProperties
data class Teacher(
    val uid: String = "",
    val userId: String = "",
    val schoolId: String = "discovery-primary",
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
    val name: String
        get() = "$firstName $lastName".trim().ifBlank { if (email.isNotBlank()) email.substringBefore("@") else "Teacher" }
}

/**
 * Student Entity in Firestore (/students/{studentId})
 */
@IgnoreExtraProperties
data class Student(
    val uid: String = "",
    val userId: String = "",
    val schoolId: String = "discovery-primary",
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
    val fullName: String
        get() = "$firstName $lastName".trim().ifBlank { studentNumber.ifBlank { "Student" } }
}

/**
 * School Class Entity in Firestore (/classes/{classId})
 */
@IgnoreExtraProperties
data class SchoolClass(
    val id: String = "",
    val schoolId: String = "discovery-primary",
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
    val schoolId: String = "discovery-primary",
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
    val schoolId: String = "discovery-primary",
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
 * School Notice Entity in Firestore (/notices/{noticeId})
 */
@IgnoreExtraProperties
data class Notice(
    val id: String = "",
    val schoolId: String = "discovery-primary",
    val authorId: String = "",
    val title: String = "",
    val message: String = "",
    val targetRole: String = "",
    val published: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * Essential School Settings (/settings/school_info)
 */
@IgnoreExtraProperties
data class SchoolSettings(
    val schoolName: String = "Discovery Primary School",
    val emisNumber: String = "700140223",
    val district: String = "Johannesburg West (D12)",
    val province: String = "Gauteng",
    val principalName: String = "Mr. Raymond Peters",
    val contactEmail: String = "admin@discoveryprimary.co.za",
    val contactPhone: String = "011 672 1422",
    val academicTerm: String = "Term 3, 2026",
    val schoolId: String = "discovery-primary"
)
