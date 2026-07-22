package com.example.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Interceptor implementing exponential backoff retry strategy for transient network failures.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 500L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null
        var tryCount = 0

        while (tryCount < maxRetries) {
            try {
                response?.close()
                response = chain.proceed(request)
                if (response.isSuccessful || (response.code in 400..499 && response.code != 429)) {
                    return response
                }
            } catch (e: IOException) {
                lastException = e
            }

            tryCount++
            if (tryCount < maxRetries) {
                try {
                    Thread.sleep(initialDelayMs * tryCount)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        if (response != null) {
            return response
        } else {
            throw lastException ?: IOException("Failed request after $maxRetries retry attempts")
        }
    }
}
