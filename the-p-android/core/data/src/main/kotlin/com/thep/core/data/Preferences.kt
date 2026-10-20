package com.thep.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.map

class Preferences(private val store: DataStore<androidx.datastore.preferences.core.Preferences>) {
    private val autoRestKey = booleanPreferencesKey("auto_rest")
    val autoRest = store.data.map { it[autoRestKey] ?: true }

    suspend fun autoRest(enabled: Boolean) {
        store.edit { it[autoRestKey] = enabled }
    }
}
