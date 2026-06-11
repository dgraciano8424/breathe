package com.dgraciano.breathe.ui.pause

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.Quote
import com.dgraciano.breathe.data.repository.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PauseViewModel @Inject constructor(
    private val quoteRepo: QuoteRepository
) : ViewModel() {

    private val _quote = MutableStateFlow<Quote?>(null)
    val quote: StateFlow<Quote?> = _quote

    fun loadQuote() {
        viewModelScope.launch {
            _quote.value = quoteRepo.getRandomQuote()
        }
    }
}
