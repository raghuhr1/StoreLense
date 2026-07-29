package com.storelense.epcencoder.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.storelense.epcencoder.databinding.ActivityLoginBinding
import com.storelense.epcencoder.net.ApiClient
import com.storelense.epcencoder.net.LoginRequest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        if (username.isBlank() || password.isBlank()) {
            binding.statusText.text = "Enter username and password"
            return
        }

        binding.loginButton.isEnabled = false
        binding.statusText.text = "Logging in..."

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.login(LoginRequest(username, password))
                val body = response.body()
                if (response.isSuccessful && body?.data != null) {
                    val role = body.data.role
                    if (role != "ADMIN" && role != "STORE_MANAGER") {
                        binding.statusText.text = "Login OK but role '$role' cannot register EPCs (needs ADMIN or STORE_MANAGER)"
                        binding.loginButton.isEnabled = true
                        return@launch
                    }
                    ApiClient.accessToken = body.data.accessToken
                    startActivity(Intent(this@LoginActivity, ImportActivity::class.java))
                    finish()
                } else {
                    binding.statusText.text = "Login failed: ${response.code()} ${body?.message ?: ""}"
                    binding.loginButton.isEnabled = true
                }
            } catch (e: Exception) {
                binding.statusText.text = "Login error: ${e.message}"
                binding.loginButton.isEnabled = true
            }
        }
    }
}
