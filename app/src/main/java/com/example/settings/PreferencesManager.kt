package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Delegate preference store instantiation
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sinzore_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    val applicationContext: Context = context.applicationContext
    private val dataStore = context.dataStore

    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode_enabled")
        val BIOMETRICS_ENABLED_KEY = booleanPreferencesKey("biometrics_enabled")
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val PREFERRED_CURRENCY_KEY = stringPreferencesKey("preferred_currency")
        
        val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        val USER_UID_KEY = stringPreferencesKey("user_uid")
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_ROLE_KEY = stringPreferencesKey("user_role")
        val LAST_READ_NOTIFICATIONS_TIMESTAMP_KEY = androidx.datastore.preferences.core.longPreferencesKey("last_read_notifications_timestamp")
    }

    val lastReadNotificationsTimestampFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_READ_NOTIFICATIONS_TIMESTAMP_KEY] ?: 0L
    }

    suspend fun saveLastReadNotificationsTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_READ_NOTIFICATIONS_TIMESTAMP_KEY] = timestamp
        }
    }

    /**
     * Session management flows and functions
     */
    val isLoggedInFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN_KEY] ?: false
    }

    val userRoleFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[USER_ROLE_KEY]
    }

    val userUidFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[USER_UID_KEY]
    }

    val userNameFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[USER_NAME_KEY]
    }

    val userEmailFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[USER_EMAIL_KEY]
    }

    suspend fun saveSession(uid: String, email: String, name: String, role: String) {
        dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN_KEY] = true
            preferences[USER_UID_KEY] = uid
            preferences[USER_EMAIL_KEY] = email
            preferences[USER_NAME_KEY] = name
            preferences[USER_ROLE_KEY] = role
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN_KEY] = false
            preferences[USER_UID_KEY] = ""
            preferences[USER_EMAIL_KEY] = ""
            preferences[USER_NAME_KEY] = ""
            preferences[USER_ROLE_KEY] = ""
        }
    }

    /**
     * Observable flow indicating if system-wide Dark Theme is overridden by user.
     */
    val darkModeFlow: Flow<Boolean?> = dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY]
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    /**
     * Observable flow indicating if biometric authentication is enabled.
     */
    val biometricsEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BIOMETRICS_ENABLED_KEY] ?: false
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRICS_ENABLED_KEY] = enabled
        }
    }

    /**
     * Observable flow indicating if push notifications are enabled.
     */
    val notificationsEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED_KEY] ?: true
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    /**
     * Observable flow indicating user preferred currency symbol (e.g., KES, USD).
     */
    val preferredCurrencyFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[PREFERRED_CURRENCY_KEY] ?: "KES"
    }

    suspend fun setPreferredCurrency(currencyCode: String) {
        dataStore.edit { preferences ->
            preferences[PREFERRED_CURRENCY_KEY] = currencyCode
        }
    }
}
