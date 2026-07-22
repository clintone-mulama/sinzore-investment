package com.example.payments

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DarajaService {
    private const val TAG = "DarajaService"
    private const val SANDBOX_BASE_URL = "https://sandbox.safaricom.co.ke"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Data class to wrap Daraja STK Push API results
     */
    data class StkResponse(
        val isSuccessful: Boolean,
        val message: String,
        val merchantRequestId: String? = null,
        val checkoutRequestId: String? = null
    )

    /**
     * Helper to retrieve OAuth Access Token from Daraja Sandbox
     */
    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val consumerKey = BuildConfig.MP_CONSUMER_KEY
        val consumerSecret = BuildConfig.MP_CONSUMER_SECRET

        if (consumerKey.isBlank() || consumerSecret.isBlank()) {
            Log.e(TAG, "Consumer key or secret is blank!")
            return@withContext null
        }

        val credentials = "$consumerKey:$consumerSecret"
        val base64Auth = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val request = Request.Builder()
            .url("$SANDBOX_BASE_URL/oauth/v1/generate?grant_type=client_credentials")
            .addHeader("Authorization", base64Auth)
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                Log.d(TAG, "Token response code: ${response.code}, body: $bodyString")
                if (response.isSuccessful && bodyString.isNotEmpty()) {
                    val json = JSONObject(bodyString)
                    return@withContext json.optString("access_token", null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token", e)
        }
        return@withContext null
    }

    /**
     * Dispatches STK Push to Safaricom Daraja Sandbox
     */
    suspend fun initiateStkPush(amount: Double, phoneNumber: String): StkResponse = withContext(Dispatchers.IO) {
        // --- TEST BYPASS TOGGLE ---
        // Change BYPASS_STK_PUSH to false to restore real Safaricom M-Pesa STK Push
        val BYPASS_STK_PUSH = true
        if (BYPASS_STK_PUSH) {
            val mockCheckoutId = "WS_CO_" + System.currentTimeMillis().toString().takeLast(6) + (1000..9999).random()
            return@withContext StkResponse(
                isSuccessful = true,
                message = "Success. Payment completed successfully (Bypassed for Testing).",
                merchantRequestId = "MOCK_MR_" + (100000..999999).random(),
                checkoutRequestId = mockCheckoutId
            )
        }
        // --------------------------

        try {
            val token = getAccessToken() ?: return@withContext StkResponse(
                isSuccessful = false,
                message = "Failed to authenticate with Safaricom Daraja. Please check your credentials."
            )

            val shortcode = BuildConfig.MP_SHORTCODE
            val passkey = BuildConfig.MP_PASSKEY

            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
            val passwordSource = "$shortcode$passkey$timestamp"
            val password = Base64.encodeToString(passwordSource.toByteArray(), Base64.NO_WRAP)

            val finalAmount = amount.toInt()

            val requestJson = JSONObject().apply {
                put("BusinessShortCode", shortcode)
                put("Password", password)
                put("Timestamp", timestamp)
                put("TransactionType", "CustomerPayBillOnline")
                put("Amount", finalAmount)
                put("PartyA", phoneNumber)
                put("PartyB", shortcode)
                put("PhoneNumber", phoneNumber)
                put("CallBackURL", BuildConfig.MP_CALLBACK_URL)
                put("AccountReference", "SinzoreFund")
                put("TransactionDesc", "Seeds Contribution")
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$SANDBOX_BASE_URL/mpesa/stkpush/v1/processrequest")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseString = response.body?.string() ?: ""
                Log.d(TAG, "STK response code: ${response.code}, body: $responseString")

                if (responseString.isNotEmpty()) {
                    val json = JSONObject(responseString)
                    val responseCode = json.optString("ResponseCode", "-1")
                    val responseDesc = json.optString("ResponseDescription", "No description")
                    val merchantRequestId = json.optString("MerchantRequestID", "")
                    val checkoutRequestId = json.optString("CheckoutRequestID", "")

                    if (responseCode == "0") {
                        return@withContext StkResponse(
                            isSuccessful = true,
                            message = responseDesc,
                            merchantRequestId = merchantRequestId,
                            checkoutRequestId = checkoutRequestId
                        )
                    } else {
                        return@withContext StkResponse(
                            isSuccessful = false,
                            message = "Daraja API Error ($responseCode): $responseDesc"
                        )
                    }
                } else {
                    return@withContext StkResponse(
                        isSuccessful = false,
                        message = "Empty response received from Safaricom Daraja."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during STK push dispatch", e)
            return@withContext StkResponse(
                isSuccessful = false,
                message = "STK Push network error: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }
}
