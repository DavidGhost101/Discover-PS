package com.example.domain

import com.example.data.model.Assignment
import com.example.data.model.Notice
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Teacher
import com.example.data.model.OWNER_ROLE
import com.example.data.model.User
import com.example.data.model.UserRole
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class SchoolAccessTest {

    private fun ts(daysFromNow: Int): Timestamp =
        Timestamp(Date(System.currentTimeMillis() + daysFromNow * 86_400_000L))

    private val notices = listOf(
        Notice(id = "all", targetRole = "ALL", published = true, createdAt = ts(-3)),
        Notice(id = "parents", targetRole = "PARENT", published = true, createdAt = ts(-1)),
        Notice(id = "parents-c1", targetRole = "PARENT", classId = "c1", published = true, createdAt = ts(-2)),
        Notice(id = "parents-c2", targetRole = "PARENT", classId = "c2", published = true),
        Notice(id = "teachers", targetRole = "TEACHER", published = true),
        Notice(id = "draft", targetRole = "ALL", published = false),
        Notice(id = "my-draft", targetRole = "STUDENT", classId = "c9", published = false, authorId = "teacherA")
    )

    @Test
    fun `parent sees school-wide, parent and own-children class notices only`() {
        val visible = SchoolAccess.visibleNotices(notices, UserRole.PARENT, "parentA", setOf("c1"))
        assertEquals(listOf("parents", "parents-c1", "all"), visible.map { it.id })
    }

    @Test
    fun `parent with no children sees no class-specific notices`() {
        val visible = SchoolAccess.visibleNotices(notices, UserRole.PARENT, "parentA", emptySet())
        assertEquals(setOf("all", "parents"), visible.map { it.id }.toSet())
    }

    @Test
    fun `teacher sees teacher notices and own drafts but not parent notices`() {
        val visible = SchoolAccess.visibleNotices(notices, UserRole.TEACHER, "teacherA", setOf("c1"))
        assertEquals(setOf("all", "teachers", "my-draft"), visible.map { it.id }.toSet())
    }

    @Test
    fun `drafts are hidden from everyone except admin and author`() {
        UserRole.entries.filter { it != UserRole.ADMIN }.forEach { role ->
            val ids = SchoolAccess.visibleNotices(notices, role, "someone", setOf("c1", "c2")).map { it.id }
            assertTrue("$role saw a draft", "draft" !in ids)
        }
        assertTrue("draft" in SchoolAccess.visibleNotices(notices, UserRole.ADMIN, "admin", emptySet()).map { it.id })
    }

    @Test
    fun `assignments are limited to the given classes and sorted by due date`() {
        val list = listOf(
            Assignment(id = "late", classId = "c1", dueDate = ts(5)),
            Assignment(id = "soon", classId = "c1", dueDate = ts(1)),
            Assignment(id = "undated", classId = "c1"),
            Assignment(id = "other", classId = "c2", dueDate = ts(0))
        )
        assertEquals(listOf("soon", "late", "undated"), SchoolAccess.assignmentsForClasses(list, setOf("c1")).map { it.id })
        assertTrue(SchoolAccess.assignmentsForClasses(list, emptySet()).isEmpty())
    }

    @Test
    fun `teacher classes come from the teacher record`() {
        val classes = listOf(SchoolClass(id = "c1", name = "B"), SchoolClass(id = "c2", name = "A"), SchoolClass(id = "c3", name = "C"))
        val teacher = Teacher(uid = "t", classIds = listOf("c2", "c1"))
        assertEquals(listOf("c2", "c1"), SchoolAccess.teacherClasses(teacher, classes).map { it.id })
        assertTrue(SchoolAccess.teacherClasses(null, classes).isEmpty())
    }

    @Test
    fun `children are only students linked to the parent`() {
        val students = listOf(
            Student(uid = "s1", firstName = "Zed", parentIds = listOf("parentA")),
            Student(uid = "s2", firstName = "Amy", parentIds = listOf("parentB")),
            Student(uid = "s3", firstName = "Ann", parentIds = listOf("parentA", "parentB"))
        )
        assertEquals(listOf("s3", "s1"), SchoolAccess.childrenOf("parentA", students).map { it.uid })
        assertTrue(SchoolAccess.childrenOf("stranger", students).isEmpty())
    }

    @Test
    fun `teachers of class`() {
        val teachers = listOf(Teacher(uid = "a", firstName = "A", classIds = listOf("c1")), Teacher(uid = "b", firstName = "B", classIds = listOf("c2")))
        assertEquals(listOf("a"), SchoolAccess.teachersOfClass("c1", teachers).map { it.uid })
    }

    @Test
    fun `due status`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 10, 12, 0, 0) }.time
        fun at(day: Int, hour: Int) = Calendar.getInstance().apply { set(2026, Calendar.MARCH, day, hour, 0, 0) }.time
        assertEquals(SchoolAccess.DueStatus.NO_DUE_DATE, SchoolAccess.dueStatus(null, now))
        assertEquals(SchoolAccess.DueStatus.DUE_TODAY, SchoolAccess.dueStatus(at(10, 23), now))
        assertEquals(SchoolAccess.DueStatus.DUE_TODAY, SchoolAccess.dueStatus(at(10, 8), now))
        assertEquals(SchoolAccess.DueStatus.OVERDUE, SchoolAccess.dueStatus(at(9, 23), now))
        assertEquals(SchoolAccess.DueStatus.UPCOMING, SchoolAccess.dueStatus(at(11, 1), now))
    }

    @Test
    fun `relationship diff ignores blanks and duplicates`() {
        val (added, removed) = SchoolAccess.diff(listOf("a", "b", "", "b"), listOf("b", "c", "c", " "))
        assertEquals(setOf("c"), added)
        assertEquals(setOf("a"), removed)
    }
}

class FormValidationTest {

    @Test
    fun `owner role opens the admin portal but is never assignable`() {
        assertEquals(UserRole.ADMIN, UserRole.fromString(OWNER_ROLE))
        assertTrue(User(role = OWNER_ROLE).isProtectedOwner)
        assertFalse(User(role = "ADMIN").isProtectedOwner)
        assertFalse(FormValidation.assignableRoles.any { it.name == OWNER_ROLE })
        assertEquals(null, UserRole.fromString("OWNER"))
    }

    @Test
    fun `email validation`() {
        assertNull(FormValidation.email("parent@example.org"))
        assertNull(FormValidation.email("  o'neil.smith+kids@school.co.za "))
        assertNotNull(FormValidation.email(""))
        assertNotNull(FormValidation.email("not-an-email"))
        assertNotNull(FormValidation.email("a@b"))
    }

    @Test
    fun `password validation`() {
        assertNull(FormValidation.password("Secure123"))
        assertNotNull(FormValidation.password(""))
        assertNotNull(FormValidation.password("Ab1"))
        assertNotNull(FormValidation.password("onlyletters"))
        assertNotNull(FormValidation.password("12345678"))
    }

    @Test
    fun `phone validation`() {
        assertNull(FormValidation.phone(""))
        assertNull(FormValidation.phone("+27 82 123 4567"))
        assertNull(FormValidation.phone("082-123-4567"))
        assertNotNull(FormValidation.phone("12ab"))
        assertNotNull(FormValidation.phone("123"))
    }

    @Test
    fun `self registration can never produce a privileged role`() {
        assertEquals(UserRole.PARENT, FormValidation.selfRegistrationRole)
    }

    @Test
    fun `role parsing is strict`() {
        assertEquals(UserRole.ADMIN, UserRole.fromString(" admin "))
        assertNull(UserRole.fromString("superuser"))
        assertNull(UserRole.fromString(null))
    }
}
