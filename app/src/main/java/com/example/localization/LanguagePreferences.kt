package com.example.localization

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sportNigerDataStore: DataStore<Preferences> by preferencesDataStore(name = "sport_niger_prefs")

data class LanguagePrefsState(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val hasCompletedOnboarding: Boolean = false
)

class LanguagePreferences(private val context: Context) {
    private object Keys {
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val LANGUAGE_ONBOARDED = booleanPreferencesKey("language_onboarded")
    }

    val stateFlow: Flow<LanguagePrefsState> = context.sportNigerDataStore.data.map { prefs ->
        val code = prefs[Keys.SELECTED_LANGUAGE] ?: AppLanguage.ENGLISH.code
        val onboarded = prefs[Keys.LANGUAGE_ONBOARDED] ?: false
        LanguagePrefsState(
            language = AppLanguage.fromCode(code),
            hasCompletedOnboarding = onboarded
        )
    }

    suspend fun setLanguage(language: AppLanguage, markOnboarded: Boolean = true) {
        context.sportNigerDataStore.edit { prefs ->
            prefs[Keys.SELECTED_LANGUAGE] = language.code
            if (markOnboarded) {
                prefs[Keys.LANGUAGE_ONBOARDED] = true
            }
        }
    }
}
