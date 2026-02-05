package com.aiphotostudio.bgremover

import com.google.android.gms.common.ConnectionResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIApplicationTest {
    @Test
    fun shouldEnablePlayIntegrityOnlyWhenPlayServicesAvailable() {
        assertTrue(AIApplication.shouldEnablePlayIntegrity(ConnectionResult.SUCCESS))
        assertFalse(AIApplication.shouldEnablePlayIntegrity(ConnectionResult.SERVICE_MISSING))
        assertFalse(AIApplication.shouldEnablePlayIntegrity(ConnectionResult.SERVICE_INVALID))
    }
}
