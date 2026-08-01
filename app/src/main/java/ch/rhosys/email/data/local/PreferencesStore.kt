package ch.rhosys.email.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ch.rhosys.email.ui.theme.CatppuccinFlavor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "numaeel_prefs")

/** Non-secret app preferences: theme, onboarding state, feature toggles. */
class PreferencesStore(private val context: Context) {

    val hasCompletedOnboarding: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    val hasSeenFeatureTour: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_TOUR_SEEN] ?: false }

    val themeFlavor: Flow<CatppuccinFlavor?> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME]?.let { runCatching { CatppuccinFlavor.valueOf(it) }.getOrNull() }
    }

    val biometricLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BIOMETRIC_LOCK] ?: false }
    val adminPanelEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ADMIN_ENABLED] ?: false }
    val lastSeenChangelogVersion: Flow<String> = context.dataStore.data.map { it[KEY_CHANGELOG_VERSION] ?: "" }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = completed }
    }

    suspend fun setFeatureTourSeen(seen: Boolean) {
        context.dataStore.edit { it[KEY_TOUR_SEEN] = seen }
    }

    suspend fun setThemeFlavor(flavor: CatppuccinFlavor?) {
        context.dataStore.edit {
            if (flavor == null) it.remove(KEY_THEME) else it[KEY_THEME] = flavor.name
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setAdminPanelEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ADMIN_ENABLED] = enabled }
    }

    suspend fun setLastSeenChangelogVersion(version: String) {
        context.dataStore.edit { it[KEY_CHANGELOG_VERSION] = version }
    }

    private companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_TOUR_SEEN = booleanPreferencesKey("feature_tour_seen")
        val KEY_THEME = stringPreferencesKey("theme_flavor")
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val KEY_ADMIN_ENABLED = booleanPreferencesKey("admin_panel_enabled")
        val KEY_CHANGELOG_VERSION = stringPreferencesKey("last_seen_changelog_version")
    }
}
