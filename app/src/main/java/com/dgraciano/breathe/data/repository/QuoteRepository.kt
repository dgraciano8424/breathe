package com.dgraciano.breathe.data.repository

import com.dgraciano.breathe.data.db.QuoteDao
import com.dgraciano.breathe.data.model.Quote
import com.dgraciano.breathe.data.remote.ZenQuotesApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuoteRepository @Inject constructor(
    private val api: ZenQuotesApi,
    private val dao: QuoteDao
) {
    suspend fun getRandomQuote(): Quote? {
        if (dao.count() == 0) refreshQuotes()
        return dao.getRandom()
    }

    /**
     * A failed or empty refresh leaves the existing cache alone. Previously the delete
     * happened before the insert and outside a transaction, so a bad response could
     * empty the quote table and leave the pause screen with nothing to show.
     */
    suspend fun refreshQuotes() {
        val dtos = runCatching { api.getQuotes() }.getOrNull() ?: return
        val quotes = dtos
            .map { Quote(text = it.q.orEmpty().trim(), author = it.a.orEmpty().trim()) }
            .filter { it.text.isNotEmpty() }
        if (quotes.isEmpty()) return
        dao.replaceAll(quotes)
    }
}
