package com.yuvraj.resumescreener.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How the stored key was last confirmed against the Gemini API. */
enum class KeyStatus { MISSING, UNVERIFIED, VERIFIED }

/**
 * Configuration, including the API credential.
 *
 * The key lives in EncryptedSharedPreferences backed by an Android Keystore
 * master key. It is never written to disk in plaintext, never logged, and
 * never leaves the device except in the `x-goog-api-key` header of a Gemini
 * call. See `.migration/security.md` for the trust-boundary reasoning.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _keyStatus = MutableStateFlow(readStatus())
    val keyStatus: StateFlow<KeyStatus> = _keyStatus.asStateFlow()

    private val _parseModel = MutableStateFlow(
        prefs.getString(KEY_PARSE_MODEL, DEFAULT_PARSE_MODEL) ?: DEFAULT_PARSE_MODEL
    )
    val parseModel: StateFlow<String> = _parseModel.asStateFlow()

    private val _scoreModel = MutableStateFlow(
        prefs.getString(KEY_SCORE_MODEL, DEFAULT_SCORE_MODEL) ?: DEFAULT_SCORE_MODEL
    )
    val scoreModel: StateFlow<String> = _scoreModel.asStateFlow()

    private fun readStatus(): KeyStatus {
        val stored = prefs.getString(KEY_API, null)
        val verified = prefs.getBoolean(KEY_VERIFIED, false)
        return when {
            stored.isNullOrBlank() -> KeyStatus.MISSING
            verified -> KeyStatus.VERIFIED
            else -> KeyStatus.UNVERIFIED
        }
    }

    /**
     * Reading the key for an outbound request. Deliberately not exposed as a
     * `StateFlow` so it cannot be captured into UI state and end up in a
     * saved-state bundle or a crash report.
     */
    fun apiKeyOrNull(): String? = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    /** Null means "not configured", and callers must fail closed rather than guess. */
    fun requireApiKey(): String = apiKeyOrNull()
        ?: error("No Gemini API key configured. The user must set one in Settings.")

    fun saveApiKey(key: String) {
        val cleaned = key.trim()
        if (cleaned.isEmpty()) {
            clearApiKey()
            return
        }
        prefs.edit()
            .putString(KEY_API, cleaned)
            .putBoolean(KEY_VERIFIED, false)
            .apply()
        _keyStatus.value = readStatus()
    }

    fun markVerified() {
        prefs.edit().putBoolean(KEY_VERIFIED, true).apply()
        _keyStatus.value = readStatus()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).remove(KEY_VERIFIED).apply()
        _keyStatus.value = readStatus()
    }

    fun setParseModel(model: String) {
        prefs.edit().putString(KEY_PARSE_MODEL, model).apply()
        _parseModel.value = model
    }

    fun setScoreModel(model: String) {
        prefs.edit().putString(KEY_SCORE_MODEL, model).apply()
        _scoreModel.value = model
    }

    companion object {
        private const val FILE_NAME = "resume_screener_settings"
        private const val KEY_API = "gemini_api_key"
        private const val KEY_VERIFIED = "gemini_api_key_verified"
        private const val KEY_PARSE_MODEL = "parse_model"
        private const val KEY_SCORE_MODEL = "score_model"

        /**
         * Rolling aliases, not the pinned ids from the Python source.
         *
         * `gemini-2.5-pro`, which the source pipeline used for scoring, now
         * returns 404 "no longer available to new users" (verified live). A
         * pinned version breaks the app the day Google retires it, so the
         * defaults point at `*-latest`, which Google keeps pointing at the
         * current model of that tier.
         */
        const val DEFAULT_PARSE_MODEL = "gemini-flash-latest"
        const val DEFAULT_SCORE_MODEL = "gemini-pro-latest"

        val PARSE_MODEL_OPTIONS = listOf(
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
        )
        val SCORE_MODEL_OPTIONS = listOf(
            "gemini-pro-latest",
            "gemini-3.1-pro-preview",
            "gemini-flash-latest",
        )
    }
}
