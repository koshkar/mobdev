package io.github.mobdev

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ContactsState {
    data object Idle : ContactsState
    data object Loading : ContactsState
    data class Loaded(val contacts: List<Contact>) : ContactsState
}

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ContactsState>(ContactsState.Idle)
    val state: StateFlow<ContactsState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value !is ContactsState.Idle) return
        _state.value = ContactsState.Loading
        viewModelScope.launch {
            val contacts = withContext(Dispatchers.IO) {
                getApplication<Application>().fetchAllContacts()
            }
            _state.value = ContactsState.Loaded(contacts)
        }
    }

    fun reset() {
        _state.value = ContactsState.Idle
    }
}
