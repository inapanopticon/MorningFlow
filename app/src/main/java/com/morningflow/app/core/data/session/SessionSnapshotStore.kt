package com.morningflow.app.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Where a running session had got to: enough to put the user back on the same step if
 * Android ends the app in the background mid-morning.
 */
data class SessionSnapshot(
    val templateId: String,
    val stepIndex: Int,
    val startedAt: Instant,
    val savedAt: Instant,
)

interface SessionSnapshotStore {
    suspend fun load(): SessionSnapshot?
    suspend fun save(snapshot: SessionSnapshot)
    suspend fun clear()
}

class InMemorySessionSnapshotStore : SessionSnapshotStore {
    private var snapshot: SessionSnapshot? = null
    override suspend fun load(): SessionSnapshot? = snapshot
    override suspend fun save(snapshot: SessionSnapshot) { this.snapshot = snapshot }
    override suspend fun clear() { snapshot = null }
}

class DataStoreSessionSnapshotStore(private val dataStore: DataStore<Preferences>) : SessionSnapshotStore {
    override suspend fun load(): SessionSnapshot? {
        val prefs = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
        return SessionSnapshot(
            templateId = prefs[TEMPLATE_ID] ?: return null,
            stepIndex = prefs[STEP_INDEX] ?: return null,
            startedAt = Instant.ofEpochMilli(prefs[STARTED_AT] ?: return null),
            savedAt = Instant.ofEpochMilli(prefs[SAVED_AT] ?: return null),
        )
    }

    override suspend fun save(snapshot: SessionSnapshot) {
        dataStore.edit {
            it[TEMPLATE_ID] = snapshot.templateId
            it[STEP_INDEX] = snapshot.stepIndex
            it[STARTED_AT] = snapshot.startedAt.toEpochMilli()
            it[SAVED_AT] = snapshot.savedAt.toEpochMilli()
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val TEMPLATE_ID = stringPreferencesKey("template_id")
        val STEP_INDEX = intPreferencesKey("step_index")
        val STARTED_AT = longPreferencesKey("started_at")
        val SAVED_AT = longPreferencesKey("saved_at")
    }
}
