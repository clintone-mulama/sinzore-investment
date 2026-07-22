package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.common.GlobalErrorHandlerWrapper
import com.example.navigation.AppNavGraph
import com.example.settings.PreferencesManager
import com.example.ui.theme.SinzoreInvestmentTheme

class MainActivity : FragmentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request POST_NOTIFICATIONS permission on Android 13 (API 33) and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    android.util.Log.i("MainActivity", "Notification permission granted")
                } else {
                    android.util.Log.w("MainActivity", "Notification permission denied")
                }
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Verify Firebase initialization
        try {
            val firebaseApp = com.google.firebase.FirebaseApp.getInstance()
            android.util.Log.i("MainActivity", "Firebase is active. Project ID: ${firebaseApp.options.projectId}")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Firebase check failed in MainActivity: ${e.message}", e)
        }
        
        // Initialize DataStore preferences manager
        val preferencesManager = PreferencesManager(applicationContext)

        enableEdgeToEdge()
        setContent {
            // Read preferred theme state dynamically
            val userDarkModePref by preferencesManager.darkModeFlow.collectAsState(initial = null)
            val useDarkTheme = userDarkModePref ?: isSystemInDarkTheme()

            SinzoreInvestmentTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Wrap with global error visual wrapper
                    GlobalErrorHandlerWrapper {
                        AppNavGraph(preferencesManager = preferencesManager)
                    }
                }
            }
        }
    }
}
