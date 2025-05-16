package com.shaghaf.map2waze

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("Map2WazePrefs", MODE_PRIVATE)
        enableEdgeToEdge()
        
        // Log the intent action and data
        Log.d("Map2Waze", "Intent Action: ${intent.action}")
        Log.d("Map2Waze", "Intent Type: ${intent.type}")
        Log.d("Map2Waze", "Intent Data: ${intent.data}")
        
        // Handle the intent
        val sharedText = when {
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            }
            intent.data != null -> {
                intent.data.toString()
            }
            else -> ""
        }
        
        Log.d("Map2Waze", "Shared Text: $sharedText")
        
        setContent {
            Map2WazeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        sharedText = sharedText,
                        prefs = prefs
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Log the new intent
        Log.d("Map2Waze", "New Intent Action: ${intent?.action}")
        Log.d("Map2Waze", "New Intent Type: ${intent?.type}")
        Log.d("Map2Waze", "New Intent Data: ${intent?.data}")
        
        // Handle the new intent
        val sharedText = when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            }
            intent?.data != null -> {
                intent.data.toString()
            }
            else -> ""
        }
        
        Log.d("Map2Waze", "New Shared Text: $sharedText")
        
        // Update the UI with the new shared text
        setContent {
            Map2WazeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        sharedText = sharedText,
                        prefs = prefs
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
fun MainScreen(
    modifier: Modifier = Modifier,
    sharedText: String,
    prefs: SharedPreferences
) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wazeUrl by remember { mutableStateOf<String?>(null) }
    var wazeWebUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Test mode variables
    var isTestMode by remember { mutableStateOf(prefs.getBoolean("isTestMode", false)) }
    val testUrl = remember { "https://maps.app.goo.gl/fZFbPgdBDYpkcwVSA" }
    
    // Auto-open setting
    var autoOpenWaze by remember { mutableStateOf(prefs.getBoolean("autoOpenWaze", true)) }

    fun openWaze(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // If Waze app is not installed, open in browser
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeWebUrl))
                context.startActivity(webIntent)
                Toast.makeText(context, "Waze app not found. Opening in browser.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("Map2Waze", "Error opening Waze", e)
            // Fallback to browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeWebUrl))
            context.startActivity(webIntent)
            Toast.makeText(context, "Error opening Waze. Opening in browser.", Toast.LENGTH_LONG).show()
        }
    }
    
    // Use test URL if in test mode, otherwise use shared text
    val urlToProcess = remember(sharedText, isTestMode, testUrl) {
        if (isTestMode) testUrl else sharedText
    }

    // Save settings when they change
    LaunchedEffect(isTestMode, autoOpenWaze) {
        prefs.edit().apply {
            putBoolean("isTestMode", isTestMode)
            putBoolean("autoOpenWaze", autoOpenWaze)
            apply()
        }
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
                    wazeWebUrl = jsonResponse.getString("waze_web_url")
                    
                    if (autoOpenWaze) {
                        // Auto-open Waze app if enabled
                        openWaze(wazeUrl!!)
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Settings section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Test Mode",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isTestMode,
                        onCheckedChange = { isTestMode = it }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-open Waze",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoOpenWaze,
                        onCheckedChange = { autoOpenWaze = it }
                    )
                }
            }
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { openWaze(wazeUrl!!) }
                        ) {
                            Text("Open in Waze App")
                        }
                        
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeWebUrl))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Open in Browser")
                        }
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