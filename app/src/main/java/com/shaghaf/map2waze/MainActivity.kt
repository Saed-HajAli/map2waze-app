package com.shaghaf.map2waze

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shaghaf.map2waze.ui.theme.Map2WazeTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Map2WazeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, sharedText: String) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wazeUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Test mode variables
    val isTestMode = remember { true }
    val testUrl = remember { "https://maps.app.goo.gl/he2tcSbZv1V8otFc9" }
    
    // Use test URL if in test mode, otherwise use shared text
    val urlToProcess = remember(sharedText, isTestMode, testUrl) {
        if (isTestMode) testUrl else sharedText
    }

    LaunchedEffect(urlToProcess) {
        if (urlToProcess.isNotEmpty()) {
            isLoading = true
            errorMessage = null
            scope.launch {
                try {
                    val encodedUrl = Uri.encode(urlToProcess)
                    val apiUrl = "https://faas-fra1-afec6ce7.doserverless.co/api/v1/web/fn-b547621a-40ef-4dbd-8b08-aa9b8bd6c273/default/map2waze?url=$encodedUrl"
                    val response = URL(apiUrl).readText()
                    val jsonResponse = JSONObject(response)
                    wazeUrl = jsonResponse.getString("waze_app_url")
                    
                    if (isTestMode) {
                        // In test mode, just show the Waze URL
                        isLoading = false
                    } else {
                        // In normal mode, open Waze app
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeUrl))
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "An error occurred"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        } else if (isTestMode && wazeUrl != null) {
            Text(
                text = "Waze URL: $wazeUrl",
                textAlign = TextAlign.Center
            )
        } else if (urlToProcess.isEmpty()) {
            Text(
                text = "Share a Google Maps link to convert it to Waze",
                textAlign = TextAlign.Center
            )
        }
    }
}