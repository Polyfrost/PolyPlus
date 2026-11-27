package org.polyfrost.polyplus.test
//? if > 1.8.9 {

import com.sun.jna.Function
import com.sun.jna.Platform
import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.system.jemalloc.MallocMessageCallback
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ForeignThreadCallbackTest {
    @Test
    fun `an lwjgl callback runs on a thread the jvm did not start`() {
        assumeTrue(Platform.isWindows() && Platform.is64Bit())
        val marker = 0x5EED_1234L
        val received = AtomicLong()
        val thread = AtomicReference<Thread>()
        val done = CountDownLatch(1)
        val callback = MallocMessageCallback.create { data, _ ->
            received.set(data)
            thread.set(Thread.currentThread())
            done.countDown()
        }
        try {
            val handle = Function.getFunction("kernel32", "CreateThread").invokePointer(
                arrayOf(null, 0L, Pointer(callback.address()), Pointer(marker), 0, null),
            )
            assertNotEquals(null, handle)
            assertTrue(done.await(5, TimeUnit.SECONDS), "the native thread never reached the callback")
            Function.getFunction("kernel32", "WaitForSingleObject").invokeInt(arrayOf(handle, 5_000))
            Function.getFunction("kernel32", "CloseHandle").invokeInt(arrayOf(handle))
        } finally {
            callback.free()
        }
        assertEquals(marker, received.get())
        assertNotEquals(Thread.currentThread(), thread.get())
    }
}
//?}
