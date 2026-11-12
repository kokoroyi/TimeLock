package com.timelock.app.data.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timelock.app.domain.model.UsageSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "timelock_history",
)

/** 使用历史（意图记录 + 时长）的归属者。与设置分开存储，避免写竞争。 */
class UsageHistoryStore(private val context: Context) {

    private val sessionsKey = stringPreferencesKey("sessions")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serializer = ListSerializer(UsageSession.serializer())

    val sessions: Flow<List<UsageSession>> = context.historyDataStore.data.map { prefs ->
        prefs[sessionsKey]
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?: emptyList()
    }

    /** 新增或按 id 覆盖一条记录。 */
    suspend fun upsert(session: UsageSession) {
        context.historyDataStore.edit { prefs ->
            val current = read(prefs[sessionsKey])
            val next = current.filterNot { it.id == session.id } + session
            prefs[sessionsKey] = json.encodeToString(serializer, next.sortedBy { it.startedAt })
        }
    }

    /** 只保留 [keepAfterMillis] 之后的记录，并限制总条数，防止无限增长。 */
    suspend fun prune(keepAfterMillis: Long, maxEntries: Int = 5000) {
        context.historyDataStore.edit { prefs ->
            val current = read(prefs[sessionsKey])
            val trimmed = current
                .filter { it.startedAt >= keepAfterMillis }
                .takeLast(maxEntries)
            prefs[sessionsKey] = json.encodeToString(serializer, trimmed)
        }
    }

    suspend fun clear() {
        context.historyDataStore.edit { prefs -> prefs.remove(sessionsKey) }
    }

    private fun read(raw: String?): List<UsageSession> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
}
