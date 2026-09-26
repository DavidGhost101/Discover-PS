package com.example.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.data.firebase.FirebaseProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real app UI against the Firebase Emulator Suite, covering the cross-role
 * workflows end to end. Requires `firebase emulators:start --only auth,firestore` on the host.
 */
@RunWith(AndroidJUnit4::class)
class SchoolWorkflowsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private val adminEmail = "principal@example.org"
    private val adminPassword = "Admin12345"
    private val teacherEmail = "nomsa.khumalo@example.org"
    private val parentEmail = "lindiwe.mokoena@example.org"

    @Before
    fun setUp() {
        EmulatorBackend.reset()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FirebaseProvider.initForEmulator(context, EmulatorBackend.HOST, EmulatorBackend.PROJECT)
        FirebaseProvider.auth.signOut()
        val adminUid = EmulatorBackend.createLogin(adminEmail, adminPassword)
        EmulatorBackend.markEmailVerified(adminUid)
        EmulatorBackend.writeProfile(adminUid, adminEmail, "Principal Admin", "ADMIN", active = true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        FirebaseProvider.auth.signOut()
        scenario?.close()
    }

    // ---------- helpers ----------

    private fun waitForText(text: String, timeoutMs: Long = 45_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 45_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitGone(tag: String, timeoutMs: Long = 45_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    private fun assertNoText(text: String) {
        compose.waitForIdle()
        check(compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()) { "\"$text\" must not be visible" }
    }

    private fun tag(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag)
    /** Scrolls the node into view when it lives in a scrollable container. */
    private fun SemanticsNodeInteraction.reveal(): SemanticsNodeInteraction =
        apply { runCatching { performScrollTo() } }

    private fun type(tag: String, value: String) { tag(tag).reveal().performTextReplacement(value) }
    private fun click(tag: String) { tag(tag).reveal().performClick() }
    private fun clickText(text: String) { compose.onAllNodesWithText(text).onFirst().reveal().performClick() }

    private fun submitForm() {
        click("form_confirm_button")
        waitGone("form_confirm_button")
    }

    private fun signIn(email: String, password: String) {
        waitForTag("login_email_input")
        type("login_email_input", email)
        type("login_password_input", password)
        click("login_submit_button")
    }

    private fun signOut() {
        tag("header_sign_out_button").performClick()
        waitForTag("login_email_input")
    }

    private fun openTab(name: String) = click("tab_$name")

    private fun choose(selectTag: String, optionText: String) {
        click(selectTag)
        waitForText(optionText)
        compose.onAllNodes(hasText(optionText)).onFirst().performClick()
    }

    // ---------- tests ----------

    @Test
    fun adminTeacherParentWorkflow() {
        // Invalid password is rejected with a visible error.
        signIn(adminEmail, "wrong-password")
        waitForTag("login_error")

        // ADMIN signs in and builds the school structure.
        signIn(adminEmail, adminPassword)
        waitForText("Admin Console")

        openTab("classes")
        click("admin_add_class_button")
        type("class_name_input", "Grade 4A"); type("class_grade_input", "Grade 4"); submitForm()
        waitForText("Grade 4A")
        click("admin_add_class_button")
        type("class_name_input", "Grade 5B"); type("class_grade_input", "Grade 5"); submitForm()
        waitForText("Grade 5B")

        openTab("subjects")
        click("admin_add_subject_button")
        type("subject_name_input", "Mathematics"); type("subject_code_input", "MATH-4")
        clickText("Grade 4A (Grade 4)")
        submitForm()
        waitForText("Classes: Grade 4A")

        // WORKFLOW 1: admin creates a teacher (login + class + subject).
        openTab("teachers")
        click("admin_add_teacher_button")
        type("teacher_first_name_input", "Nomsa"); type("teacher_last_name_input", "Khumalo")
        type("teacher_email_input", teacherEmail); type("teacher_emp_input", "EMP-102")
        clickText("Grade 4A (Grade 4)")
        clickText("Mathematics")
        submitForm()
        waitForText("Nomsa Khumalo")
        waitForText("Classes: Grade 4A")
        waitForText("LOGIN ACTIVE")

        // WORKFLOW 2: admin enrols learners in two different classes.
        openTab("students")
        click("admin_add_student_button")
        type("student_first_name_input", "Thabo"); type("student_last_name_input", "Mokoena"); type("student_number_input", "DPS-1001")
        choose("student_class_select", "Grade 4A (Grade 4)")
        submitForm()
        waitForText("Thabo Mokoena")
        click("admin_add_student_button")
        type("student_first_name_input", "Sipho"); type("student_last_name_input", "Other"); type("student_number_input", "DPS-2001")
        choose("student_class_select", "Grade 5B (Grade 5)")
        submitForm()
        waitForText("Sipho Other")

        // WORKFLOW 3: admin creates a parent account linked to Thabo only.
        compose.onAllNodes(hasText("+ Parent account")).onFirst().assertExists()
        check(compose.onAllNodes(tagPrefix("add_parent_")).fetchSemanticsNodes().size == 2)
        // Rows are sorted by name: "Sipho Other" first, "Thabo Mokoena" second.
        compose.onAllNodes(tagPrefix("add_parent_"))[1].reveal().performClick()
        type("new_parent_name_input", "Lindiwe Mokoena"); type("new_parent_email_input", parentEmail)
        submitForm()
        waitForText("Parents: Lindiwe Mokoena")

        // WORKFLOW 6: notices targeted at parents and at teachers.
        openTab("notices")
        click("admin_add_notice_button")
        type("notice_title_input", "Parents meeting"); type("notice_message_input", "Friday 18:00 in the hall")
        choose("notice_audience_select", "Parents")
        submitForm()
        waitForText("Parents meeting")
        click("admin_add_notice_button")
        type("notice_title_input", "Staff briefing"); type("notice_message_input", "Monday 07:30")
        choose("notice_audience_select", "Teachers")
        submitForm()
        waitForText("Staff briefing")

        // Dashboard numbers come from Firestore.
        openTab("dashboard")
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("stat_students_value") and hasText("2")).fetchSemanticsNodes().isNotEmpty()
        }
        tag("stat_teachers_value").assertExists()
        compose.onNode(hasTestTag("stat_teachers_value") and hasText("1")).assertExists()
        compose.onNode(hasTestTag("stat_classes_value") and hasText("2")).assertExists()
        compose.onNode(hasTestTag("stat_notices_value") and hasText("2")).assertExists()
        signOut()

        // TEACHER sets a password from the emailed link and signs in.
        check(EmulatorBackend.hasEmail(teacherEmail, "PASSWORD_RESET"))
        EmulatorBackend.completePasswordReset(teacherEmail, "Teacher12345")
        signIn(teacherEmail, "Teacher12345")
        waitForText("Teacher Portal")
        waitForText("Thabo Mokoena")
        assertNoText("Sipho Other")
        assertNoText("Grade 5B")

        openTab("notices")
        waitForText("Staff briefing")
        assertNoText("Parents meeting")

        // WORKFLOW 5: teacher publishes an assignment for their class.
        openTab("assignments")
        click("teacher_create_assignment_button")
        type("assignment_title_input", "Fractions worksheet")
        type("assignment_description_input", "Complete pages 42-45")
        submitForm()
        waitForText("Fractions worksheet")
        signOut()

        // PARENT signs in and sees only their child, the homework and the parent notice.
        EmulatorBackend.completePasswordReset(parentEmail, "Parent12345")
        signIn(parentEmail, "Parent12345")
        waitForText("Parent Portal")
        waitForText("Thabo Mokoena")
        waitForText("Nomsa Khumalo")
        assertNoText("Sipho Other")

        openTab("homework")
        waitForText("Fractions worksheet")

        openTab("notices")
        waitForText("Parents meeting")
        assertNoText("Staff briefing")

        // Profile edit persists across sign-out / sign-in.
        openTab("profile")
        click("profile_edit_button")
        type("profile_name_input", "Lindiwe M. Mokoena")
        click("profile_save_button")
        waitForText("Lindiwe M. Mokoena")
        signOut()
        signIn(parentEmail, "Parent12345")
        waitForText("Parent Portal")
        openTab("profile")
        waitForText("Lindiwe M. Mokoena")
        signOut()

        // WORKFLOW 7: admin moves Thabo to Grade 5B; teacher of 4A loses access, parent sees the new class.
        signIn(adminEmail, adminPassword)
        waitForText("Admin Console")
        openTab("students")
        waitForText("Thabo Mokoena")
        compose.onAllNodes(tagPrefix("edit_student_"))[1].reveal().performClick()
        choose("student_class_select", "Grade 5B (Grade 5)")
        submitForm()
        signOut()

        signIn(teacherEmail, "Teacher12345")
        waitForText("Teacher Portal")
        openTab("my_students")
        waitForText("No learners in your classes yet.")
        assertNoText("Thabo Mokoena")
        signOut()

        signIn(parentEmail, "Parent12345")
        waitForText("Parent Portal")
        waitForText("Grade 5B")
        openTab("homework")
        waitForText("No assignments for Grade 5B yet.")
        signOut()
    }

    @Test
    fun parentSelfRegistrationRequiresVerificationAndApproval() {
        val email = "new.parent@example.org"
        waitForTag("login_mode_register")
        tag("login_mode_register").performClick()
        type("register_name_input", "New Parent")
        type("register_email_input", email)
        type("register_password_input", "Parent12345")
        type("register_confirm_input", "Parent12345")
        click("register_submit_button")

        waitForTag("verify_email_screen")
        // Registration finishes (profile written, verification email sent) before actions unlock.
        waitForText("Account created. Check your inbox")
        click("verify_email_check_button")
        waitForText("Your email address is not verified yet.")

        EmulatorBackend.completeEmailVerification(email)
        click("verify_email_check_button")
        waitForTag("access_denied_screen")
        waitForText("Your account is not active", timeoutMs = 45_000)
        click("access_denied_sign_out")
        waitForTag("login_email_input")
    }

    @Test
    fun forgotPasswordSendsResetEmail() {
        waitForTag("login_email_input")
        type("login_email_input", adminEmail)
        click("login_forgot_password_button")
        waitForTag("login_info")
        check(EmulatorBackend.hasEmail(adminEmail, "PASSWORD_RESET"))
        EmulatorBackend.completePasswordReset(adminEmail, "NewAdmin12345")
        signIn(adminEmail, "NewAdmin12345")
        waitForText("Admin Console")
        signOut()
    }
}
