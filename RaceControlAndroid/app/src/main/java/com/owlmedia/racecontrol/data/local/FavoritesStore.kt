package com.owlmedia.racecontrol.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by
    preferencesDataStore("racecontrol_favorites")

/**
 * Favourite drivers and teams.
 *
 * Same keys as iOS (`favorite_drivers`, `favorite_teams`) so the concept, and
 * the pin-to-top behaviour that depends on it, matches across platforms.
 */
@Singleton
class FavoritesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DRIVERS = stringSetPreferencesKey("favorite_drivers")
        val TEAMS = stringSetPreferencesKey("favorite_teams")
    }

    val driverIds: Flow<Set<String>> =
        context.favoritesDataStore.data.map { it[Keys.DRIVERS] ?: emptySet() }

    val teamIds: Flow<Set<String>> =
        context.favoritesDataStore.data.map { it[Keys.TEAMS] ?: emptySet() }

    /** Returns true if the id is now a favourite, for haptic/announcement use. */
    suspend fun toggleDriver(id: String): Boolean = toggle(Keys.DRIVERS, id)

    suspend fun toggleTeam(id: String): Boolean = toggle(Keys.TEAMS, id)

    private suspend fun toggle(key: Preferences.Key<Set<String>>, id: String): Boolean {
        var added = false
        context.favoritesDataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            added = id !in current
            prefs[key] = if (added) current + id else current - id
        }
        return added
    }
}
