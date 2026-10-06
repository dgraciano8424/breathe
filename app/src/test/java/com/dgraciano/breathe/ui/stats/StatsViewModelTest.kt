package com.dgraciano.breathe.ui.stats

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.InterventionEvent
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.widget.WidgetRefresher
import io.mockk.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: StatsRepository
    private lateinit var widgets: WidgetRefresher
    private lateinit var resolver: ContentResolver
    private lateinit var context: Context
    private val created = mutableListOf<StatsViewModel>()
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = mockk {
            coEvery { getTodayTotalAttempts() } returns 4
            coEvery { getTodayDeclined() } returns 2
            coEvery { getWeeklyTotalAttempts() } returns 4
            coEvery { getWeeklyDeclined() } returns 2
            coEvery { getTodayMinutesSaved() } returns 40
            coEvery { getWeeklyMinutesSaved() } returns 40
            coEvery { getTopAppsThisWeek() } returns emptyList()
            coEvery { clearHistory() } returns Unit
            coEvery { getHistory() } returns listOf(InterventionEvent(packageName = "com.demo", appName = "消息", timestamp = 123, outcome = "OPENED"))
        }
        widgets = mockk(relaxed = true)
        resolver = mockk()
        context = mockk { every { contentResolver } returns resolver }
    }
    private fun vm() = StatsViewModel(repo, widgets, context, dispatcher).also { created.add(it); it.loadStats() }
    @After fun teardown() { created.forEach { it.viewModelScope.cancel() }; created.clear(); Dispatchers.resetMain() }

    @Test fun `export writes UTF-8 without deleting history or changing totals`() = runTest {
        val uri = mockk<Uri>(); val stream = ByteArrayOutputStream()
        every { resolver.openOutputStream(uri, "wt") } returns stream
        val model = vm(); model.exportHistory(uri)
        assertTrue(stream.toString("UTF-8").contains("消息"))
        assertTrue(model.notice.value!!.startsWith("History exported"))
        assertEquals(4, model.state.value.todayAttempts)
        assertFalse(model.working.value)
        coVerify(exactly = 0) { repo.clearHistory() }
    }
    @Test fun `failed file write reports failure and permits retry`() = runTest {
        val uri = mockk<Uri>()
        every { resolver.openOutputStream(uri, "wt") } throws java.io.IOException("unavailable")
        val model = vm(); model.exportHistory(uri)
        assertTrue(model.notice.value!!.contains("could not finish"))
        assertFalse(model.working.value)
        val stream = ByteArrayOutputStream()
        every { resolver.openOutputStream(uri, "wt") } returns stream
        model.exportHistory(uri)
        assertTrue(model.notice.value!!.startsWith("History exported"))
    }
    @Test fun `failed deletion preserves visible totals and allows retry`() = runTest {
        coEvery { repo.clearHistory() } throws java.io.IOException("database failure")
        val model = vm(); model.clearHistory()
        assertEquals(4, model.state.value.todayAttempts)
        assertTrue(model.notice.value!!.contains("could not be cleared"))
        assertFalse(model.working.value)
        verify(exactly = 0) { widgets.refresh() }
    }
    @Test fun `clearing cancels a stale load and duplicate requests are ignored`() = runTest {
        val oldLoad = CompletableDeferred<Int>()
        val deletion = CompletableDeferred<Unit>()
        var reads = 0
        coEvery { repo.getTodayTotalAttempts() } coAnswers { if (reads++ == 0) oldLoad.await() else 0 }
        coEvery { repo.clearHistory() } coAnswers { deletion.await() }
        coEvery { repo.getTodayDeclined() } returns 0
        val model = vm(); model.clearHistory(); model.clearHistory()
        assertTrue(model.working.value)
        deletion.complete(Unit); oldLoad.complete(99)
        assertEquals(0, model.state.value.todayAttempts)
        assertTrue(model.notice.value!!.startsWith("History cleared"))
        assertFalse(model.working.value)
        coVerify(exactly = 1) { repo.clearHistory() }
        verify(exactly = 1) { widgets.refresh() }
    }
}
