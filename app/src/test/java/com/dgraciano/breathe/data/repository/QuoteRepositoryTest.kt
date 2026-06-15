package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.QuoteDao
import com.dgraciano.breathe.data.model.Quote
import com.dgraciano.breathe.data.remote.QuoteDto
import com.dgraciano.breathe.data.remote.ZenQuotesApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class QuoteRepositoryTest {

    private lateinit var api: ZenQuotesApi
    private lateinit var dao: QuoteDao
    private lateinit var repo: QuoteRepository

    @Before
    fun setUp() {
        api = mockk()
        dao = mockk()
        repo = QuoteRepository(api, dao)
    }

    @Test
    fun `getRandomQuote returns from dao without refresh when quotes exist`() = runTest {
        val quote = Quote(text = "Test", author = "Author")
        coEvery { dao.count() } returns 5
        coEvery { dao.getRandom() } returns quote

        val result = repo.getRandomQuote()

        assertEquals(quote, result)
        coVerify(exactly = 0) { api.getQuotes() }
    }

    @Test
    fun `getRandomQuote triggers refresh when dao is empty`() = runTest {
        val dto = QuoteDto(q = "Test quote", a = "Author", h = "")
        val quote = Quote(text = "Test quote", author = "Author")
        coEvery { dao.count() } returns 0
        coEvery { api.getQuotes() } returns listOf(dto)
        coEvery { dao.deleteAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit
        coEvery { dao.getRandom() } returns quote

        val result = repo.getRandomQuote()

        coVerify { api.getQuotes() }
        assertEquals(quote, result)
    }

    @Test
    fun `getRandomQuote returns null when dao empty and api fails`() = runTest {
        coEvery { dao.count() } returns 0
        coEvery { api.getQuotes() } throws RuntimeException("Network error")
        coEvery { dao.getRandom() } returns null

        val result = repo.getRandomQuote()

        assertNull(result)
        // No crash — error is swallowed
    }

    @Test
    fun `refreshQuotes maps dto fields to quote text and author`() = runTest {
        val dto = QuoteDto(q = "Seize the day", a = "Caesar", h = "<h>test</h>")
        coEvery { api.getQuotes() } returns listOf(dto)
        val slot = slot<List<Quote>>()
        coEvery { dao.deleteAll() } returns Unit
        coEvery { dao.insertAll(capture(slot)) } returns Unit

        repo.refreshQuotes()

        val inserted = slot.captured
        assertEquals(1, inserted.size)
        assertEquals("Seize the day", inserted[0].text)
        assertEquals("Caesar", inserted[0].author)
    }

    @Test
    fun `refreshQuotes inserts multiple quotes from api`() = runTest {
        val dtos = listOf(
            QuoteDto(q = "Quote 1", a = "Author 1", h = ""),
            QuoteDto(q = "Quote 2", a = "Author 2", h = ""),
            QuoteDto(q = "Quote 3", a = "Author 3", h = "")
        )
        coEvery { api.getQuotes() } returns dtos
        val slot = slot<List<Quote>>()
        coEvery { dao.deleteAll() } returns Unit
        coEvery { dao.insertAll(capture(slot)) } returns Unit

        repo.refreshQuotes()

        assertEquals(3, slot.captured.size)
    }

    @Test
    fun `refreshQuotes clears old quotes before inserting new ones`() = runTest {
        coEvery { api.getQuotes() } returns listOf(QuoteDto(q = "Q", a = "A", h = ""))
        coEvery { dao.deleteAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit

        repo.refreshQuotes()

        coVerifyOrder {
            dao.deleteAll()
            dao.insertAll(any())
        }
    }

    @Test
    fun `refreshQuotes swallows api exception without crashing`() = runTest {
        coEvery { api.getQuotes() } throws RuntimeException("Timeout")

        // Must not throw
        repo.refreshQuotes()

        coVerify(exactly = 0) { dao.deleteAll() }
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `refreshQuotes does nothing when api returns empty list`() = runTest {
        coEvery { api.getQuotes() } returns emptyList()
        coEvery { dao.deleteAll() } returns Unit
        coEvery { dao.insertAll(any()) } returns Unit

        repo.refreshQuotes()

        // Empty list — deleteAll + insertAll([]) still called (non-null response)
        coVerify { dao.deleteAll() }
    }
}
