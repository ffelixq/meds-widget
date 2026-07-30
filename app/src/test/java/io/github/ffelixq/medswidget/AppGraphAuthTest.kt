package io.github.ffelixq.medswidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppGraphAuthTest {
    @Test
    fun `cold widget callback uses authoritative Firebase uid before session flow initializes`() {
        assertEquals(
            "firebase-user",
            selectAuthenticatedUid(
                authoritativeAuthAvailable = true,
                authoritativeUid = "firebase-user",
                sessionUid = null,
            ),
        )
    }

    @Test
    fun `session uid is only a fallback and missing auth remains signed out`() {
        assertEquals(
            "session-user",
            selectAuthenticatedUid(
                authoritativeAuthAvailable = false,
                authoritativeUid = null,
                sessionUid = "session-user",
            ),
        )
        assertNull(
            selectAuthenticatedUid(
                authoritativeAuthAvailable = false,
                authoritativeUid = null,
                sessionUid = null,
            ),
        )
        assertNull(
            selectAuthenticatedUid(
                authoritativeAuthAvailable = true,
                authoritativeUid = null,
                sessionUid = "stale-session-user",
            ),
        )
    }
}
