package com.dgraciano.breathe.data.remote

import com.google.gson.annotations.SerializedName

data class QuoteDto(
    @SerializedName("q") val q: String,
    @SerializedName("a") val a: String,
    @SerializedName("h") val h: String
)
