package com.example.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * OkHttp Interceptor providing automatic Firebase ID Token attachment and token refresh handling.
 */
class AuthInterceptor(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentUser = firebaseAuth.currentUser

        var token: String? = null
        if (currentUser != null) {
            try {
                val task = currentUser.getIdToken(false)
                val tokenResult = Tasks.await(task, 10, TimeUnit.SECONDS)
                token = tokenResult?.token
            } catch (e: Exception) {
                // Ignore transient token fetch errors
            }
        }

        val requestBuilder = originalRequest.newBuilder()
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        var response = chain.proceed(requestBuilder.build())

        // Handle 401 Unauthorized by forcing a token refresh and retrying once
        if (response.code == 401 && currentUser != null) {
            response.close()
            try {
                val refreshTask = currentUser.getIdToken(true)
                val refreshTokenResult = Tasks.await(refreshTask, 10, TimeUnit.SECONDS)
                val refreshedToken = refreshTokenResult?.token
                if (!refreshedToken.isNullOrEmpty()) {
                    val retryRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $refreshedToken")
                        .build()
                    response = chain.proceed(retryRequest)
                }
            } catch (e: Exception) {
                // Refresh failed
            }
        }

        return response
    }
}
