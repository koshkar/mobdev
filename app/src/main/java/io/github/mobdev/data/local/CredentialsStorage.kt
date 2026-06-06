package io.github.mobdev.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.mobdev.data.Credentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.credentialsDataStore by preferencesDataStore(name = "credentials")

class CredentialsStorage(private val context: Context) {

    val credentials: Flow<Credentials?> = context.credentialsDataStore.data.map { prefs ->
        val username = prefs[KEY_USERNAME]
        val password = prefs[KEY_PASSWORD]
        if (username.isNullOrBlank() || password.isNullOrBlank()) null
        else Credentials(username, password)
    }

    suspend fun save(credentials: Credentials) {
        context.credentialsDataStore.edit { prefs ->
            prefs[KEY_USERNAME] = credentials.username
            prefs[KEY_PASSWORD] = credentials.password
        }
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { prefs ->
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_PASSWORD)
        }
    }

    private companion object {
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD = stringPreferencesKey("password")
    }
}
