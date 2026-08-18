package com.dgraciano.breathe.widget

import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PauseCountWidgetTest {

    @Test
    fun `widget update rethrows cancellation without reporting an ordinary error`() = runTest {
        val cancellation = CancellationException("receiver cancelled")
        val reported = mutableListOf<Exception>()

        val thrown = runCatching {
            runWidgetUpdate(
                load = { throw cancellation },
                update = {},
                reportError = reported::add,
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(emptyList<Exception>(), reported)
    }

    @Test
    fun `widget update reports an ordinary read failure`() = runTest {
        val failure = IllegalStateException("database closed")
        val reported = mutableListOf<Exception>()

        runWidgetUpdate(
            load = { throw failure },
            update = {},
            reportError = reported::add,
        )

        assertEquals(listOf(failure), reported)
    }
}
