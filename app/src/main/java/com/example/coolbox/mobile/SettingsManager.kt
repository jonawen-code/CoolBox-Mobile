package com.example.coolbox.mobile

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SettingsManager {
    private const val PREFS_NAME = "coolbox_settings"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
    private const val KEY_FRIDGES = "fridges_v2"
    private const val KEY_FRIDGE_BASES = "fridge_bases_v2"
    private const val KEY_FRIDGE_CAPS = "fridge_caps_v2"
    private const val KEY_CATEGORIES = "categories_v2"
    private const val KEY_LAST_SYNC_MS = "last_sync_ms"
    
    // Legacy Keys for Migration
    private const val OLD_KEY_FRIDGES = "fridges"
    private const val OLD_KEY_FRIDGE_BASES = "fridge_bases"
    private const val OLD_KEY_CATEGORIES = "categories"
    private const val OLD_KEY_FRIDGE_CAPS = "fridge_caps"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isSetupComplete(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SETUP_COMPLETE, false)
    fun setSetupComplete(context: Context, complete: Boolean) = getPrefs(context).edit().putBoolean(KEY_SETUP_COMPLETE, complete).apply()

    fun getServerUrl(context: Context): String = getPrefs(context).getString(KEY_SERVER_URL, "http://192.168.31.94:3001/coolbox") ?: "http://192.168.31.94:3001/coolbox"
    fun setServerUrl(context: Context, url: String) = getPrefs(context).edit().putString(KEY_SERVER_URL, url).apply()

    // --- List Storage (JSON) ---
    private fun getList(context: Context, key: String, oldKey: String, default: List<String>): List<String> {
        val json = getPrefs(context).getString(key, null)
        if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            return gson.fromJson(json, type) ?: default
        }
        // Migration
        val legacy = getPrefs(context).getString(oldKey, null)
        if (legacy != null) {
            val migrated = legacy.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            setList(context, key, migrated)
            return migrated
        }
        return default
    }

    private fun setList(context: Context, key: String, list: List<String>) {
        getPrefs(context).edit().putString(key, gson.toJson(list)).apply()
    }

    fun getFridgeBases(context: Context): List<String> = getList(context, KEY_FRIDGE_BASES, OLD_KEY_FRIDGE_BASES, listOf("我的冰箱", "小冰柜"))
    fun setFridgeBases(context: Context, bases: List<String>) = setList(context, KEY_FRIDGE_BASES, bases)

    fun getFridges(context: Context): List<String> = getList(context, KEY_FRIDGES, OLD_KEY_FRIDGES, emptyList())
    fun setFridges(context: Context, fridges: List<String>) = setList(context, KEY_FRIDGES, fridges)

    fun getCategories(context: Context): List<String> = getList(context, KEY_CATEGORIES, OLD_KEY_CATEGORIES, listOf("肉蛋水产", "奶品饮料", "速冻食品", "蔬菜水果", "熟食剩菜"))
    fun setCategories(context: Context, cats: List<String>) = setList(context, KEY_CATEGORIES, cats)

    fun getFridgeCaps(context: Context): Map<String, String> {
        val json = getPrefs(context).getString(KEY_FRIDGE_CAPS, null)
        if (json != null) {
            val type = object : TypeToken<Map<String, String>>() {}.type
            return gson.fromJson(json, type) ?: emptyMap()
        }
        // Migration
        val legacy = getPrefs(context).getString(OLD_KEY_FRIDGE_CAPS, "") ?: ""
        if (legacy.isNotEmpty()) {
            val migrated = legacy.split(";").filter { it.contains(":") }.associate { 
                val p = it.split(":")
                p[0] to p[1]
            }
            setFridgeCaps(context, migrated)
            return migrated
        }
        return emptyMap()
    }
    fun setFridgeCaps(context: Context, caps: Map<String, String>) {
        getPrefs(context).edit().putString(KEY_FRIDGE_CAPS, gson.toJson(caps)).apply()
    }
    
    fun getLastSyncMs(context: Context): Long = getPrefs(context).getLong(KEY_LAST_SYNC_MS, 0L)
    fun setLastSyncMs(context: Context, timeMs: Long) = getPrefs(context).edit().putLong(KEY_LAST_SYNC_MS, timeMs).apply()

    fun normalizeAllKeys(context: Context) {
        val fridges = getFridges(context)
        val caps = getFridgeCaps(context)
        
        val normalizedFridges = fridges.map { com.example.coolbox.mobile.util.NaturalSortUtils.normalizeHierarchyFormat(it) }.distinct()
        val normalizedCaps = caps.mapKeys { com.example.coolbox.mobile.util.NaturalSortUtils.normalizeHierarchyFormat(it.key) }
        
        setFridges(context, normalizedFridges)
        setFridgeCaps(context, normalizedCaps)
    }

    /**
     * V3.0.0-Pre26: Clean up legacy keys after a successful takeover to ensure no shadow data exists.
     */
    fun clearLegacyKeys(context: Context) {
        getPrefs(context).edit().apply {
            remove(OLD_KEY_FRIDGES)
            remove(OLD_KEY_FRIDGE_BASES)
            remove(OLD_KEY_CATEGORIES)
            remove(OLD_KEY_FRIDGE_CAPS)
        }.apply()
    }
}
