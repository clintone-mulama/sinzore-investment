package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class SinzoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val app = FirebaseApp.initializeApp(this)
            if (app != null) {
                val currentProjectId = app.options.projectId
                Log.d("SinzoreApplication", "FirebaseApp initialized successfully! Project Name: $currentProjectId")
                
                // Detect Firebase project migration to clear cached references and allow testing again
                val sharedPrefs = getSharedPreferences("sinzore_init_prefs", MODE_PRIVATE)
                val savedProjectId = sharedPrefs.getString("active_project_id", "")
                if (savedProjectId != currentProjectId) {
                    Log.i("SinzoreApplication", "Firebase Project changed from '$savedProjectId' to '$currentProjectId'. Resetting cached test status.")
                    
                    // Clear the previous active login session if any, since it belongs to the old project
                    try {
                        FirebaseAuth.getInstance().signOut()
                    } catch (e: Exception) {
                        Log.w("SinzoreApplication", "Failed to sign out on project migration: ${e.message}")
                    }
                    
                    // Reset test flags so we can verify the new project works
                    sharedPrefs.edit()
                        .putString("active_project_id", currentProjectId)
                        .putBoolean("auth_test_completed", false)
                        .apply()
                }
                
                // Now test Firebase Authentication by creating a temporary user account (Task 10)
                testFirebaseAuthentication()
            } else {
                Log.e("SinzoreApplication", "FirebaseApp initialization returned null.")
            }
        } catch (e: Exception) {
            Log.e("SinzoreApplication", "FirebaseApp initialization failed!", e)
        }
    }

    private fun testFirebaseAuthentication() {
        try {
            val sharedPrefs = getSharedPreferences("sinzore_init_prefs", MODE_PRIVATE)
            if (sharedPrefs.getBoolean("auth_test_completed", false)) {
                Log.d("SinzoreApplication", "Firebase Auth test already run successfully. Skipping.")
                return
            }

            // Immediately mark completed to prevent multiple runs or blocking successive startups
            sharedPrefs.edit().putBoolean("auth_test_completed", true).apply()

            // Run on a separate thread to ensure we never block the main thread during Application.onCreate()
            Thread {
                try {
                    val auth = FirebaseAuth.getInstance()
                    Log.d("SinzoreApplication", "FirebaseAuth instance retrieved successfully in background.")
                    
                    // Avoid disrupting any existing active login session
                    if (auth.currentUser != null) {
                        Log.d("SinzoreApplication", "An active session exists. Skipping Auth test to preserve session.")
                        return@Thread
                    }
                    
                    // Create a temporary email & password to test sign-up
                    val testEmail = "test_firebase_init_${System.currentTimeMillis()}@example.com"
                    val testPassword = "TempSecurePassword123!"
                    
                    auth.createUserWithEmailAndPassword(testEmail, testPassword)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.i("SinzoreApplication", "Firebase Auth temporary user registration test: SUCCESS. Created user with email: $testEmail")
                                
                                // Clean up: delete the temporary test user
                                val user = auth.currentUser
                                user?.delete()?.addOnCompleteListener { deleteTask ->
                                    if (deleteTask.isSuccessful) {
                                        Log.i("SinzoreApplication", "Firebase Auth test user cleanup: SUCCESS. Test user deleted.")
                                    } else {
                                        Log.w("SinzoreApplication", "Firebase Auth test user cleanup failed: ${deleteTask.exception?.message}")
                                    }
                                    auth.signOut() // Make sure auth is signed out cleanly
                                }
                            } else {
                                Log.e("SinzoreApplication", "Firebase Auth temporary user registration test: FAILED.", task.exception)
                            }
                        }
                } catch (ex: Exception) {
                    Log.e("SinzoreApplication", "Exception inside background Firebase Auth test thread.", ex)
                }
            }.start()
        } catch (e: Exception) {
            Log.e("SinzoreApplication", "Exception preparing Firebase Auth test.", e)
        }
    }
}
