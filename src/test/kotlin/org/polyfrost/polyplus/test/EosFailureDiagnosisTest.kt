package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.network.eos.EosFailureDiagnosis

class EosFailureDiagnosisTest {
    @Test
    fun `hitting the descriptor ceiling is called out even when the backend is down too`() {
        val explanation = EosFailureDiagnosis.explain(EosFailureDiagnosis.FD_SETSIZE, backendReachable = false)
        assertNotNull(explanation)
        assertTrue(explanation!!.contains("FD_SETSIZE"))
    }

    @Test
    fun `a healthy descriptor count with a reachable backend blames EOS reachability`() {
        val explanation = EosFailureDiagnosis.explain(1023, backendReachable = true)
        assertNotNull(explanation)
        assertTrue(explanation!!.contains("api.epicgames.dev"))
    }

    @Test
    fun `an offline game says nothing beyond the raw EOS error`() {
        assertNull(EosFailureDiagnosis.explain(1023, backendReachable = false))
        assertNull(EosFailureDiagnosis.explain(null, backendReachable = false))
    }
}
