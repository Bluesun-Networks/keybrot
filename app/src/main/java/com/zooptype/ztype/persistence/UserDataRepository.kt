package com.zooptype.ztype.persistence

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * UserDataRepository: Persistence layer for user-specific data.
 *
 * Stores:
 * - Word frequency boosts (how often the user has selected each word)
 * - Bigram/trigram context data (word pair frequencies)
 * - User preferences (theme, density, etc.)
 *
 * Architecture:
 * - v1: Local SharedPreferences + JSON serialization
 * - Future: Room database with optional cloud sync
 *
 * The abstraction layer means swapping to Room/cloud later
 * requires zero changes to calling code.
 */
class UserDataRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("keybrot_user_data", Context.MODE_PRIVATE)

    private val gson = Gson()

    // --- Word Frequency ---

    fun saveWordFrequencies(frequencies: Map<String, Int>) {
        val json = gson.toJson(frequencies)
        prefs.edit().putString(KEY_WORD_FREQ, json).apply()
    }

    fun loadWordFrequencies(): Map<String, Int> {
        val json = prefs.getString(KEY_WORD_FREQ, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // --- Bigram Data ---

    fun saveBigramData(bigrams: Map<String, Map<String, Int>>) {
        val json = gson.toJson(bigrams)
        prefs.edit().putString(KEY_BIGRAMS, json).apply()
    }

    fun loadBigramData(): Map<String, Map<String, Int>> {
        val json = prefs.getString(KEY_BIGRAMS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Map<String, Int>>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // --- Settings Convenience ---

    fun getThemeName(): String = prefs.getString(KEY_THEME, "CYBER_LUMINESCENT") ?: "CYBER_LUMINESCENT"
    fun setThemeName(name: String) = prefs.edit().putString(KEY_THEME, name).apply()

    fun getNodeDensity(): String = prefs.getString(KEY_DENSITY, "standard") ?: "standard"
    fun setNodeDensity(density: String) = prefs.edit().putString(KEY_DENSITY, density).apply()

    fun getHapticsEnabled(): Boolean = prefs.getBoolean(KEY_HAPTICS, true)
    fun setHapticsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()

    fun getHapticIntensity(): Float = prefs.getInt(KEY_HAPTIC_INTENSITY, 80) / 100f
    fun setHapticIntensity(intensity: Float) = prefs.edit().putInt(KEY_HAPTIC_INTENSITY, (intensity * 100).toInt()).apply()

    fun getAdaptiveLearning(): Boolean = prefs.getBoolean(KEY_ADAPTIVE, true)
    fun setAdaptiveLearning(enabled: Boolean) = prefs.edit().putBoolean(KEY_ADAPTIVE, enabled).apply()

    fun getDebugOverlay(): Boolean = prefs.getBoolean(KEY_DEBUG, true)
    fun setDebugOverlay(enabled: Boolean) = prefs.edit().putBoolean(KEY_DEBUG, enabled).apply()

    /**
     * Get the max visible nodes based on density setting.
     */
    fun getMaxVisibleNodes(): Int {
        return when (getNodeDensity()) {
            "minimal" -> 5
            "standard" -> 26
            "full" -> 50
            else -> 26
        }
    }

    /**
     * Clear all user learning data.
     */
    fun clearLearningData() {
        prefs.edit()
            .remove(KEY_WORD_FREQ)
            .remove(KEY_BIGRAMS)
            .apply()
    }

    companion object {
        private const val KEY_WORD_FREQ = "word_frequencies"
        private const val KEY_BIGRAMS = "bigram_data"
        private const val KEY_THEME = "theme"
        private const val KEY_DENSITY = "node_density"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_HAPTIC_INTENSITY = "haptic_intensity"
        private const val KEY_ADAPTIVE = "adaptive_learning"
        private const val KEY_DEBUG = "debug_overlay"
    }
}
