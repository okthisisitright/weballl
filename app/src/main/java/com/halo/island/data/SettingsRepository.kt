package com.halo.island.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.halo.island.core.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

val Context.haloStore by preferencesDataStore("halo")

class SettingsRepository(private val context: Context, private val scope: CoroutineScope) {
    val loaded = CompletableDeferred<Unit>()
    private val key = stringPreferencesKey("settings_v1")
    private val _state = MutableStateFlow(HaloSettings())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            context.haloStore.data
                .catch {
                    if (it is IOException)
                        emit(androidx.datastore.preferences.core.emptyPreferences())
                    else throw it
                }
                .map { SettingsCodec.decode(it[key].orEmpty()) }
                .collect { saved ->
                    _state.value = saved
                    // Readiness means consumers can immediately read the restored value.
                    loaded.complete(Unit)
                }
        }
    }

    suspend fun update(change: (HaloSettings) -> HaloSettings) {
        context.haloStore.edit {
            val before = SettingsCodec.decode(it[key].orEmpty())
            val after = change(before).validated()
            if (after != before) it[key] = SettingsCodec.encode(after)
        }
    }

    private val custom = stringPreferencesKey("custom_material")

    suspend fun saveCustom() {
        context.haloStore.edit { it[custom] = it[key] ?: SettingsCodec.encode(HaloSettings()) }
    }

    suspend fun restoreCustom(): Boolean {
        val saved = context.haloStore.data.first()[custom] ?: return false
        val style = SettingsCodec.decode(saved)
        update { old ->
            style.copy(
                enabled = old.enabled,
                privacy = old.privacy,
                exclusions = old.exclusions,
                bootReminder = old.bootReminder,
            )
        }
        return true
    }
}
