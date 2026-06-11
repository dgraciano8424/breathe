package com.dgraciano.breathe.data.remote

import retrofit2.http.GET

interface ZenQuotesApi {
    @GET("quotes")
    suspend fun getQuotes(): List<QuoteDto>
}
