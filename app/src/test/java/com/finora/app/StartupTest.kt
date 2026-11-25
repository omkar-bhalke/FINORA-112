package com.finora.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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

    @Test fun signedOutInstallOpensFirebaseLogin() {
        assertEquals("finoraaaaaa", FirebaseApp.getInstance().options.projectId)
        app.onNodeWithText("Continue with Google").assertIsDisplayed()
        app.onNodeWithText("Firebase setup required").assertDoesNotExist()
    }
}
