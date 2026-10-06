package com.dgraciano.breathe.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgraciano.breathe.data.model.UserProgress
import com.dgraciano.breathe.data.repository.AchievementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val repo: AchievementRepository
) : ViewModel() {

    private val _progress = MutableStateFlow<UserProgress?>(null)
    val progress: StateFlow<UserProgress?> = _progress
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private var loadJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _progress.value = repo.getUserProgress()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _errorMessage.value = "Couldn't load your journey. Try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
