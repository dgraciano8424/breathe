package com.dgraciano.breathe.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Fields are nullable because Gson will happily leave them null on a malformed or
 * partial response — declaring them non-null only hides that until it crashes.
 */
data class QuoteDto(
    @SerializedName("q") val q: String?,
    @SerializedName("a") val a: String?,
    @SerializedName("h") val h: String?
)
