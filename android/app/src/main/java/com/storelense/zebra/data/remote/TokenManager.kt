package com.storelense.zebra.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "storelense_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // Access token stored in memory only (JWT — contains no PII beyond userId)
    @Volatile private var _accessToken: String? = null

    fun setTokens(accessToken: String, refreshToken: String) {
        _accessToken = accessToken
        prefs.edit().putString(KEY_REFRESH, refreshToken).apply()
    }

    fun getAccessToken(): String?  = _accessToken
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    fun clearTokens() {
        _accessToken = null
        prefs.edit().remove(KEY_REFRESH).apply()
        Timber.d("Tokens cleared")
    }

    fun isLoggedIn(): Boolean = getRefreshToken() != null

    fun saveUser(userId: String, username: String, role: String, storeId: String?) {
        prefs.edit()
            .putString(KEY_USER_ID,   userId)
            .putString(KEY_USERNAME,  username)
            .putString(KEY_ROLE,      role)
            .putString(KEY_STORE_ID,  storeId)
            .apply()
    }

    fun getUserId():  String? = prefs.getString(KEY_USER_ID,  null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getRole():    String? = prefs.getString(KEY_ROLE,     null)
    fun getStoreId(): String? = prefs.getString(KEY_STORE_ID, null)

    companion object {
        private const val KEY_REFRESH  = "refresh_token"
        private const val KEY_USER_ID  = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE     = "role"
        private const val KEY_STORE_ID = "store_id"
    }
}
