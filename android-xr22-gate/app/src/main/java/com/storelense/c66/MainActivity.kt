package com.storelense.c66

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.storelense.c66.data.remote.TokenManager
import com.storelense.c66.ui.navigation.AppNavigation
import com.storelense.c66.ui.theme.C66Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            C66Theme {
                AppNavigation(startLoggedIn = tokenManager.isLoggedIn)
            }
        }
    }
}
