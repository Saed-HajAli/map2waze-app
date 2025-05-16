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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shaghaf.map2waze.MainActivity.Companion.addDebugLog
import com.shaghaf.map2waze.ui.theme.Map2WazeTheme
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class MapResponse(
    val formatted_address: String,
    val google_maps_url: String,
    val latitude: Double,
    val longitude: Double,
    val waze_app_url: String,
    val waze_web_url: String
)

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private val debugLogs = mutableStateListOf<String>()

    companion object {
        private var instance: MainActivity? = null
        
        fun addDebugLog(message: String) {
            instance?.let { activity ->
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val logMessage = "[$timestamp] $message"
                Log.d("Map2Waze", message)
                activity.debugLogs.add(logMessage)
                if (activity.debugLogs.size > 100) {
                    activity.debugLogs.removeAt(0)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        prefs = getSharedPreferences("Map2WazePrefs", MODE_PRIVATE)
        enableEdgeToEdge()

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        addDebugLog("Received shared text: $sharedText")
        
        setContent {
            Map2WazeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        sharedText = sharedText,
                        prefs = prefs,
                        debugLogs = debugLogs
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Get shared text from new intent
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        addDebugLog("Received new shared text: $sharedText")
        
        setContent {
            Map2WazeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        sharedText = sharedText,
                        prefs = prefs,
                        debugLogs = debugLogs
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    sharedText: String,
    prefs: SharedPreferences,
    debugLogs: List<String>
) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wazeUrl by remember { mutableStateOf<String?>(null) }
    var wazeWebUrl by remember { mutableStateOf<String?>(null) }
    var showDebugLogs by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // Test mode variables
    var isTestMode by remember { mutableStateOf(prefs.getBoolean("isTestMode", false)) }
    val testUrl = remember { "https://maps.app.goo.gl/fZFbPgdBDYpkcwVSA" }
    
    // Auto-open setting
    var autoOpenWaze by remember { mutableStateOf(prefs.getBoolean("autoOpenWaze", true)) }

    // Log when sharedText changes
    LaunchedEffect(sharedText) {
        addDebugLog("Processing shared text: $sharedText")
    }

    fun openWaze(url: String) {
        try {
            addDebugLog("Attempting to open Waze with URL: $url")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (intent.resolveActivity(context.packageManager) != null) {
                addDebugLog("Waze app found, launching...")
                context.startActivity(intent)
            } else {
                addDebugLog("Waze app not found, falling back to browser")
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeWebUrl))
                context.startActivity(webIntent)
                Toast.makeText(context, "Waze app not found. Opening in browser.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            addDebugLog("Error opening Waze: ${e.message}")
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeWebUrl))
            context.startActivity(webIntent)
            Toast.makeText(context, "Error opening Waze. Opening in browser.", Toast.LENGTH_LONG).show()
        }
    }
    
    // Use test URL if in test mode, otherwise use shared text
    val urlToProcess = remember(sharedText, isTestMode, testUrl) {
        val url = if (isTestMode) testUrl else sharedText
        addDebugLog("URL to process: $url (isTestMode: $isTestMode)")
        url
    }

    // Save settings when they change
    LaunchedEffect(isTestMode, autoOpenWaze) {
        addDebugLog("Settings changed - isTestMode: $isTestMode, autoOpenWaze: $autoOpenWaze")
        prefs.edit().apply {
            putBoolean("isTestMode", isTestMode)
            putBoolean("autoOpenWaze", autoOpenWaze)
            apply()
        }
    }

    LaunchedEffect(urlToProcess) {
        if (urlToProcess.isNotEmpty()) {
            addDebugLog("Starting to process URL: $urlToProcess")
            isLoading = true
            errorMessage = null
            scope.launch {
                try {
                    val response: MapResponse
                    if (isTestMode) {
                        addDebugLog("Using mock response in test mode")
                        response = MapResponse(
                            formatted_address = "8CV6+QRC - Al Ghubaiba - Halwan - Sharjah - United Arab Emirates",
                            google_maps_url = "https://www.google.com/maps?q=25.344607,55.411712",
                            latitude = 25.344607,
                            longitude = 55.411712,
                            waze_app_url = "waze://?ll=25.344607,55.411712&navigate=yes",
                            waze_web_url = "https://waze.com/ul?ll=25.344607,55.411712&navigate=yes"
                        )
                    } else {
                        val encodedUrl = Uri.encode(urlToProcess)
                        val apiUrl = "https://faas-fra1-afec6ce7.doserverless.co/api/v1/web/fn-b547621a-40ef-4dbd-8b08-aa9b8bd6c273/default/map2waze?url=$encodedUrl"
                        addDebugLog("Making API call to: $apiUrl (HttpURLConnection)")
                        try {
                            val url = URL(apiUrl)
                            val connection = url.openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.connectTimeout = 15000
                            connection.readTimeout = 15000
                            connection.setRequestProperty("Accept", "application/json")
                            val responseCode = connection.responseCode
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                val body = connection.inputStream.bufferedReader().use { it.readText() }
                                addDebugLog("API Response: $body")
                                val json = JSONObject(body)
                                response = MapResponse(
                                    formatted_address = json.optString("formatted_address"),
                                    google_maps_url = json.optString("google_maps_url"),
                                    latitude = json.optDouble("latitude"),
                                    longitude = json.optDouble("longitude"),
                                    waze_app_url = json.optString("waze_app_url"),
                                    waze_web_url = json.optString("waze_web_url")
                                )
                            } else {
                                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                                addDebugLog("API Error Response: $errorBody")
                                throw Exception("Server returned error code: $responseCode")
                            }
                            connection.disconnect()
                        } catch (e: Exception) {
                            addDebugLog("API call failed: ${e.message}")
                            throw Exception("Failed to convert URL: ${e.message}")
                        }
                    }

                    wazeUrl = response.waze_app_url
                    wazeWebUrl = response.waze_web_url
                    
                    addDebugLog("Parsed Waze URLs - App: $wazeUrl, Web: $wazeWebUrl")
                    
                    if (autoOpenWaze) {
                        addDebugLog("Auto-opening Waze app")
                        openWaze(wazeUrl!!)
                    }
                } catch (e: Exception) {
                    addDebugLog("Error processing URL: ${e.message}")
                    errorMessage = "Error: ${e.message}\nPlease try again later."
                } finally {
                    isLoading = false
                }
            }
        } else {
            addDebugLog("No URL to process")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
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
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Debug Logs",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showDebugLogs,
                        onCheckedChange = { showDebugLogs = it }
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
        
        // Debug Logs Section
        if (showDebugLogs) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Debug Logs",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    debugLogs.forEach { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}