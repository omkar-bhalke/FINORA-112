package com.finora.app

import com.google.firebase.auth.FirebaseAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33], application = android.app.Application::class)
class AuthMessageTest {
    @Test fun signInFailureOffersRecoveryWithoutRevealingWhetherAccountExists() {
        val incorrect = authMessage(FirebaseAuthException("ERROR_INVALID_CREDENTIAL", "internal detail"))
        val unknown = authMessage(FirebaseAuthException("ERROR_USER_NOT_FOUND", "internal detail"))
        assertEquals(incorrect, unknown)
        assertTrue(incorrect.contains("reset"))
    }
}
