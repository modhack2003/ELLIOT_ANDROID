package com.eiliot.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Persisted settings; the legacy library is used only to migrate the old encrypted vault. */
class SecureStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("eiliot_v2_settings", Context.MODE_PRIVATE)
    init {
        if (!prefs.getBoolean("migration_done", false)) {
            val oldFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/eiliot_secure.xml")
            if (oldFile.exists()) {
                runCatching {
                    @Suppress("DEPRECATION")
                    val old = EncryptedSharedPreferences.create(context, "eiliot_secure",
                        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
                    baseUrl = old.getString("base_url", baseUrl).orEmpty()
                    model = old.getString("model", model).orEmpty()
                    apiKey = old.getString("api_key", "").orEmpty()
                    onboarded = old.getBoolean("onboarded", false)
                    orbEnabled = old.getBoolean("orb_enabled", true)
                    ttsPitch = old.getFloat("tts_pitch", 1f)
                    ttsRate = old.getFloat("tts_rate", 1f)
                    prefs.edit().putBoolean("migration_done", true).apply()
                }.onFailure { prefs.edit().putBoolean("migration_problem", true).apply() }
            } else prefs.edit().putBoolean("migration_done", true).apply()
        }
    }
    init { if(!prefs.contains("notification_awareness"))prefs.edit().putBoolean("notification_awareness",proactiveEnabled).commit() }
    private fun str(k: String, default: String = "") = prefs.getString(k, default).orEmpty()
    private fun put(k: String, v: String) { prefs.edit().putString(k, v).apply() }
    private fun bool(k: String, default: Boolean = false) = prefs.getBoolean(k, default)
    private fun put(k: String, v: Boolean) { prefs.edit().putBoolean(k, v).commit() }
    var baseUrl: String get() = str("base_url", "https://api.river.ai/v1"); set(v) = put("base_url", v.trim())
    var model: String get() = str("model", "deepseek-ai/DeepSeek-V4.1-Flash"); set(v) = put("model", v.trim())
    var apiKey: String
        get() = str("api_key_cipher").let { if (it.isBlank()) "" else runCatching { CryptoVault.open(it) }.getOrDefault("") }
        set(v) = put("api_key_cipher", if (v.isBlank()) "" else CryptoVault.seal(v.trim()))
    var onboarded: Boolean get() = bool("onboarded"); set(v) = put("onboarded", v)
    var isInitialSetupComplete:Boolean get()=bool("initial_setup_v061_complete");set(v)=put("initial_setup_v061_complete",v)
    var masterEnabled: Boolean get() = bool("master_enabled"); set(v) = put("master_enabled", v)
    var proactiveEnabled: Boolean get() = bool("proactive_enabled"); set(v) = put("proactive_enabled", v)
    var reviewActions: Boolean get() = bool("review_actions"); set(v) = put("review_actions", v)
    var roomMode: Boolean get() = bool("room_mode"); set(v) = put("room_mode", v)
    var followUpEnabled: Boolean get() = bool("follow_up_enabled", true); set(v) = put("follow_up_enabled", v)
    var checkInInterval: Int get() = prefs.getInt("checkin_interval", 60).coerceIn(5, 1440); set(v) { prefs.edit().putInt("checkin_interval", v.coerceIn(5,1440)).commit() }
    var checkInStart: Int get() = prefs.getInt("checkin_start", 480).coerceIn(0,1439); set(v) { prefs.edit().putInt("checkin_start", v.coerceIn(0,1439)).commit() }
    var checkInEnd: Int get() = prefs.getInt("checkin_end", 1320).coerceIn(0,1439); set(v) { prefs.edit().putInt("checkin_end", v.coerceIn(0,1439)).commit() }
    var checkInSnooze: Long get() = prefs.getLong("checkin_snooze", 0); set(v) { prefs.edit().putLong("checkin_snooze", v).commit() }
    var lastCheckIn: Long get() = prefs.getLong("last_checkin", 0); set(v) { prefs.edit().putLong("last_checkin", v).commit() }
    var notificationContents: Boolean get() = bool("notification_contents"); set(v) = put("notification_contents", v)
    var notificationAwarenessEnabled:Boolean get()=bool("notification_awareness",proactiveEnabled);set(v)=put("notification_awareness",v)
    var noticesWhenLocked: Boolean get() = bool("notices_when_locked"); set(v) = put("notices_when_locked", v)
    var orbEnabled: Boolean get() = bool("orb_enabled", true); set(v) = put("orb_enabled", v)
    var wakeEnabled: Boolean get() = bool("wake_enabled"); set(v) = put("wake_enabled", v)
    var learningEnabled: Boolean get() = bool("learning_enabled"); set(v) = put("learning_enabled", v)
    var memoryEnabled: Boolean get() = bool("memory_enabled", true); set(v) = put("memory_enabled", v)
    var screenEnabled: Boolean get() = bool("screen_enabled"); set(v) = put("screen_enabled", v)
    var phoneToolsEnabled: Boolean get() = bool("phone_tools_enabled", true); set(v) = put("phone_tools_enabled", v)
    var webToolsEnabled: Boolean get() = bool("web_tools_enabled", true); set(v) = put("web_tools_enabled", v)
    var controlEnabled: Boolean get() = bool("phone_control_enabled"); set(v) = put("phone_control_enabled", v)
    var orbInApp: Boolean get() = bool("orb_in_app", true); set(v) = put("orb_in_app", v)
    var inferenceMode: String get() = str("inference_mode", "cloud"); set(v) = put("inference_mode", v)
    var notificationApps: String get() = str("notification_apps"); set(v) = put("notification_apps", v)
    var ttsVoice: String get() = str("tts_voice"); set(v) = put("tts_voice", v)
    var confirmedMaleVoices: Set<String>
        get() = prefs.getStringSet("male_voices", emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet("male_voices", v.toSet()).apply() }
    var ttsPitch: Float get() = prefs.getFloat("tts_pitch", 1f); set(v) { prefs.edit().putFloat("tts_pitch", v).apply() }
    var ttsRate: Float get() = prefs.getFloat("tts_rate", 1f); set(v) { prefs.edit().putFloat("tts_rate", v).apply() }
    var lastProactiveAt: Long get() = prefs.getLong("last_proactive", 0); set(v) { prefs.edit().putLong("last_proactive", v).apply() }
    val migrationProblem: Boolean get() = bool("migration_problem")
}
