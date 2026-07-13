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

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "storelense_tokens",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("TokenManager", "Keystore/Encryption error, falling back to plain prefs", e)
        // Fallback to plain SharedPreferences if hardware Keystore is broken (ErrorCode -10003)
        context.getSharedPreferences("storelense_tokens_plain", Context.MODE_PRIVATE)
    }

    var accessToken:  String? get() = prefs.getString(KEY_ACCESS,  null); set(v) = prefs.edit().putString(KEY_ACCESS,  v).apply()
    var refreshToken: String? get() = prefs.getString(KEY_REFRESH, null); set(v) = prefs.edit().putString(KEY_REFRESH, v).apply()
    var userId:       String? get() = prefs.getString(KEY_USER_ID, null); set(v) = prefs.edit().putString(KEY_USER_ID, v).apply()
    var username:     String? get() = prefs.getString(KEY_USERNAME,null); set(v) = prefs.edit().putString(KEY_USERNAME,v).apply()
    var role:         String? get() = prefs.getString(KEY_ROLE,    null); set(v) = prefs.edit().putString(KEY_ROLE,    v).apply()
    var storeId:      String? get() = prefs.getString(KEY_STORE,   null); set(v) = prefs.edit().putString(KEY_STORE,   v).apply()

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
