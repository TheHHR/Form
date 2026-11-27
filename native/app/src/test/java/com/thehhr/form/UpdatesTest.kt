package com.thehhr.form

import com.thehhr.form.data.Updates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun compares() {
        assertTrue(Updates.isNewer("4.0.3", "4.0.2"))
        assertTrue(Updates.isNewer("4.1.0", "4.0.9"))
        assertTrue(Updates.isNewer("4.0.3", "4.0.2-beta"))
        assertFalse(Updates.isNewer("4.0.2", "4.0.2"))
        assertFalse(Updates.isNewer("4.0.10", "4.0.11"))
        assertFalse(Updates.isNewer("3.6.3", "4.0.0"))
    }
}
