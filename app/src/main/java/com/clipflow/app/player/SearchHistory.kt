package com.clipflow.app.player

import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * What was searched in ClipFlow, newest first (local only, never sent to the account). A repeat
 * moves to the top; matching ignores case, the latest spelling wins.
 */
object SearchHistory {
    private const val KEY = "queries"
    private const val MAX = 50
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var prefs: SharedPreferences? = null
    @Volatile
    private var mutated = false

    var entries by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        mutated = false
        ioScope.launch {
            val p = appContext.getSharedPreferences("search", Context.MODE_PRIVATE).also { prefs = it }
            val json = p.getString(KEY, null) ?: return@launch
            val parsed = runCatching {
                val array = JSONArray(json)
                List(array.length()) { array.getString(it) }
            }.getOrElse { emptyList() }
            withContext(Dispatchers.Main.immediate) {
                if (!mutated && entries.isEmpty()) entries = parsed
            }
        }
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        save(listOf(q) + entries.filterNot { it.equals(q, ignoreCase = true) })
    }

    fun remove(query: String) = save(entries - query)

    fun clear() = save(emptyList())

    /** Entries containing [query] (all of them for a blank query). */
    fun matching(query: String): List<String> {
        val q = query.trim()
        return if (q.isEmpty()) entries else entries.filter { it.contains(q, ignoreCase = true) }
    }

    private fun save(list: List<String>) {
        mutated = true
        val clipped = list.take(MAX)
        entries = clipped
        ioScope.launch {
            prefs?.edit()?.putString(KEY, JSONArray(clipped).toString())?.apply()
        }
    }
}
