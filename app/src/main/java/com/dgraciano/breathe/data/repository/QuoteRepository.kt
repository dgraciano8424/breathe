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

    suspend fun refreshQuotes() {
        runCatching { api.getQuotes() }.getOrNull()?.let { dtos ->
            val quotes = dtos.map { Quote(text = it.q, author = it.a) }
            dao.deleteAll()
            dao.insertAll(quotes)
        }
    }
}
