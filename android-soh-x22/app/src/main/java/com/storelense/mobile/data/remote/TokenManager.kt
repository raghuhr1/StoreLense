package com.storelense.mobile.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext context: Context) {

    private val plainPrefs = context.getSharedPreferences("storelense_tokens_plain", Context.MODE_PRIVATE)

    private val prefs: SharedPreferences by lazy {
        // If we previously detected the Keystore was broken, don't even try to init it.
        // This prevents long hangs/ANRs on every app start.
        if (plainPrefs.getBoolean("keystore_broken", false)) {
            return@lazy plainPrefs
        }

        try {
            EncryptedSharedPreferences.create(
                context,
                "storelense_tokens",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("TokenManager", "Encryption hardware failure (Error -10003 likely), falling back", e)
            plainPrefs.edit().putBoolean("keystore_broken", true).apply()
            plainPrefs
        }
    }

    var accessToken: String?
        get() = try { prefs.getString(KEY_ACCESS, null) } catch (e: Exception) { null }
        set(v) = try { prefs.edit().putString(KEY_ACCESS, v).apply() } catch (e: Exception) { }

    var refreshToken: String?
        get() = try { prefs.getString(KEY_REFRESH, null) } catch (e: Exception) { null }
        set(v) = try { prefs.edit().putString(KEY_REFRESH, v).apply() } catch (e: Exception) { }

    var userId: String?
        get() = try { prefs.getString(KEY_USER_ID, null) } catch (e: Exception) { null }
        set(v) = try { prefs.edit().putString(KEY_USER_ID, v).apply() } catch (e: Exception) { }

    var username: String?
        get() = try { prefs.getString(KEY_USERNAME, null) } catch (e: Exception) { null }
        set(v) = try { prefs.edit().putString(KEY_USERNAME, v).apply() } catch (e: Exception) { }

    var role: String?
        get() = try { prefs.getString(KEY_ROLE, null) } catch (e: Exception) { null }
        set(v) = try { prefs.edit().putString(KEY_ROLE, v).apply() } catch (e: Exception) { }

    var storeId: String?
        get() = try { prefs.getString(KEY_STORE, null) } catch (e: Exception) { null }
        set(v) = try { prefs.edit().putString(KEY_STORE, v).apply() } catch (e: Exception) { }

    val isLoggedIn: Boolean get() = accessToken != null && refreshToken != null

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_ACCESS   = "access_token"
        private const val KEY_REFRESH  = "refresh_token"
        private const val KEY_USER_ID  = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE     = "role"
        private const val KEY_STORE    = "store_id"
    }
}
