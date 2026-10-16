package org.swasthaconnect.core.designsystem

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "skinsewa_settings")

enum class ThemeChoice { LIGHT, DARK }

class ThemePreferences(private val context: Context) {
    val choice: Flow<ThemeChoice> = context.themeDataStore.data.map { prefs ->
        if (prefs[THEME_KEY] == ThemeChoice.DARK.name) ThemeChoice.DARK else ThemeChoice.LIGHT
    }

    suspend fun set(choice: ThemeChoice) {
        context.themeDataStore.edit { it[THEME_KEY] = choice.name }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_choice")
    }
}
