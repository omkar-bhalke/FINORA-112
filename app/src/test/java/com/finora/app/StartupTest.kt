package com.finora.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinoraApplication::class)
class StartupTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test
    fun signedOutInstallOpensConfiguredProviders() {
        assertEquals("finoraaaaaa", FirebaseApp.getInstance().options.projectId)
        app.onNodeWithText("Welcome back").assertIsDisplayed()
        app.onNodeWithText("Continue with Google").performScrollTo().assertIsDisplayed()
        app.onNodeWithText("Firebase setup required").assertDoesNotExist()
    }

    @Test
    fun registerAndResetValidateBeforeSubmitting() {
        fun click(label: String) = app.onNodeWithText(label).performScrollTo().performClick()
        click("New to FINORA? Create account")
        click("Create account")
        app.onNodeWithText("Enter a valid email address.").assertExists()
        app.onNodeWithText("Email address").performTextInput("test@example.org")
        app.onNodeWithText("Password").performTextInput("abc")
        click("Create account")
        app.onNodeWithText("Use at least 6 characters for your password.").assertExists()
        click("Back to sign in")
        click("Forgot password?")
        app.onNodeWithText("Reset your password").assertExists()
        app.onNodeWithText("test@example.org").assertExists()
        app.onNodeWithText("Password").assertDoesNotExist()
        click("Back to sign in")
        app.onNodeWithText("Welcome back").assertExists()
    }
}
