package io.github.ffelixq.medswidget.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirebaseErrorMessagesTest {
    @Test
    fun unknownAccountAndWrongPasswordUseSameSignInMessage() {
        val unknownAccount = FirebaseErrorMessages.forAuthCode("ERROR_USER_NOT_FOUND")
        val wrongPassword = FirebaseErrorMessages.forAuthCode("ERROR_WRONG_PASSWORD")
        val invalidCredential = FirebaseErrorMessages.forAuthCode("ERROR_INVALID_CREDENTIAL")

        assertEquals(wrongPassword, unknownAccount)
        assertEquals(wrongPassword, invalidCredential)
    }

    @Test
    fun accountCreationFailureDoesNotConfirmExistingEmail() {
        val message = FirebaseErrorMessages.forAuthCode("ERROR_EMAIL_ALREADY_IN_USE")

        assertFalse(message.contains("already uses", ignoreCase = true))
        assertFalse(message.contains("already registered", ignoreCase = true))
    }
}
