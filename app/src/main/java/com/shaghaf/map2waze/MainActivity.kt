package com.shaghaf.map2waze

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import java.net.HttpURLConnection

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

// Mock response data
private val mockResponse = """
    {
        "formatted_address": "8CV6+QRC - Al Ghubaiba - Halwan - Sharjah - United Arab Emirates",
        "google_maps_url": "https://www.google.com/maps?q=25.344607,55.411712",
        "latitude": 25.344607,
        "longitude": 55.411712,
        "waze_app_url": "waze://?ll=25.344607,55.411712&navigate=yes",
        "waze_web_url": "https://waze.com/ul?ll=25.344607,55.411712&navigate=yes"
    }
""".trimIndent()

@Composable
fun MainScreen(modifier: Modifier = Modifier, sharedText: String) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wazeUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Test mode variables
    val isTestMode = remember { true }
    val testUrl = remember { "https://maps.app.goo.gl/fZFbPgdBDYpkcwVSA" }
    
    // Auto-open setting
    var autoOpenWaze by remember { mutableStateOf(true) }
    
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
                    val response: String
                    if (isTestMode) {
                        // Use mock response in test mode
                        Log.d("Map2Waze", "Using mock response in test mode")
                        response = mockResponse
                    } else {
                        // Make real API call
                        val encodedUrl = Uri.encode(urlToProcess)
                        val apiUrl = "https://faas-fra1-afec6ce7.doserverless.co/api/v1/web/fn-b547621a-40ef-4dbd-8b08-aa9b8bd6c273/default/map2waze?url=$encodedUrl"
                        
                        Log.d("Map2Waze", "Making real API call to: $apiUrl")
                        
                        val connection = URL(apiUrl).openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 15000
                        connection.readTimeout = 15000
                        
                        val responseCode = connection.responseCode
                        Log.d("Map2Waze", "Response Code: $responseCode")
                        
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            response = connection.inputStream.bufferedReader().use { it.readText() }
                            Log.d("Map2Waze", "Response: $response")
                        } else {
                            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                            Log.e("Map2Waze", "Error Response: $errorResponse")
                            throw Exception("Server returned error code: $responseCode")
                        }
                    }

                    // Process response (both mock and real)
                    val jsonResponse = JSONObject(response)
                    wazeUrl = jsonResponse.getString("waze_app_url")
                    
                    if (autoOpenWaze) {
                        // Auto-open Waze app if enabled
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeUrl))
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e("Map2Waze", "Error occurred", e)
                    errorMessage = "Error: ${e.message}\nPlease try again later."
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
        // Settings section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Auto-open Waze:",
                modifier = Modifier.padding(end = 8.dp)
            )
            Switch(
                checked = autoOpenWaze,
                onCheckedChange = { autoOpenWaze = it }
            )
        }

        if (isLoading) {
            CircularProgressIndicator()
        } else if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        } else if (wazeUrl != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isTestMode) {
                    Text(
                        text = "Test Mode Active",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                Text(
                    text = "Waze URL: $wazeUrl",
                    textAlign = TextAlign.Center
                )
                
                if (!autoOpenWaze) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeUrl))
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Open in Waze")
                    }
                }
            }
        } else if (urlToProcess.isEmpty()) {
            Text(
                text = "Share a Google Maps link to convert it to Waze",
                textAlign = TextAlign.Center
            )
        }
    }
}