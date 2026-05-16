package com.brainandbolt.textport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.brainandbolt.textport.data.AppContainer
import com.brainandbolt.textport.data.SecurePreferences
import com.brainandbolt.textport.ui.theme.TextPortTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppSection { Dashboard, Settings, Profile }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TextPortTheme {
                AppRoot()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { SecurePreferences(context) }
    val scope = rememberCoroutineScope()

    val normalizedDefaultUrl = BuildConfig.API_BASE_URL
    val savedUrl = prefs.apiBaseUrl()?.trim().orEmpty()
    val migratedUrl = if (
        savedUrl.isBlank() ||
        savedUrl.contains("your-server.example.com") ||
        savedUrl.contains("10.0.2.2")
    ) normalizedDefaultUrl else savedUrl
    if (savedUrl != migratedUrl) prefs.setApiBaseUrl(migratedUrl)

    var splashDone by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Welcome to TextPort.") }
    var serverUrl by remember { mutableStateOf(migratedUrl) }
    var activationCode by remember { mutableStateOf(prefs.activationCode().orEmpty()) }
    var loggedIn by remember { mutableStateOf(!prefs.token().isNullOrBlank()) }
    var syncEnabled by remember { mutableStateOf(prefs.syncEnabled()) }
    var setupCompleted by remember { mutableStateOf(prefs.setupCompleted()) }
    var section by remember { mutableStateOf(AppSection.Dashboard) }
    var showConnectionPrompt by remember { mutableStateOf(false) }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)

    val hasSmsPermissions = hasSmsPermissions(context)
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val ok = permissions.values.all { it }
        status = if (ok) "SMS permissions granted." else "SMS permissions denied."
        if (ok && !prefs.connectionVerified()) showConnectionPrompt = true
    }

    LaunchedEffect(Unit) {
        delay(1200)
        splashDone = true

        if (!prefs.permissionPrompted()) {
            prefs.setPermissionPrompted(true)
            smsPermissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
        } else if (hasSmsPermissions && !prefs.connectionVerified()) {
            showConnectionPrompt = true
        }
    }

    var lastToastMessage by remember { mutableStateOf("") }
    LaunchedEffect(status) {
        if (status.isNotBlank() && status != lastToastMessage) {
            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            lastToastMessage = status
        }
    }

    if (!splashDone) {
        SplashScreen()
        return
    }

    if (showConnectionPrompt) {
        AlertDialog(
            onDismissRequest = { showConnectionPrompt = false },
            title = { Text("Verify Connection") },
            text = { Text("Run connection test now to complete initial setup.") },
            confirmButton = {
                Button(onClick = {
                    prefs.setApiBaseUrl(serverUrl.trim())
                    scope.launch {
                        AppContainer.repository(context).testConnection().fold(
                            onSuccess = { ok ->
                                if (ok) {
                                    prefs.setConnectionVerified(true)
                                    showConnectionPrompt = false
                                    status = "Connection verified."
                                } else {
                                    status = "Connection failed."
                                }
                            },
                            onFailure = { status = "Connection failed: ${it.message}" }
                        )
                    }
                }) {
                    Text("Test Connection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConnectionPrompt = false }) { Text("Later") }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("TextPort", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Navigation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NavigationItem("Dashboard", section == AppSection.Dashboard) {
                    section = AppSection.Dashboard
                    scope.launch { drawerState.close() }
                }
                NavigationItem("Settings", section == AppSection.Settings) {
                    section = AppSection.Settings
                    scope.launch { drawerState.close() }
                }
                NavigationItem("Profile", section == AppSection.Profile) {
                    section = AppSection.Profile
                    scope.launch { drawerState.close() }
                }
            }
        }
    ) {
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (section) {
                            AppSection.Dashboard -> "Dashboard"
                            AppSection.Settings -> "Settings"
                            AppSection.Profile -> "Profile"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Text("☰", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    Text(
                        "●",
                        color = if (syncEnabled) Color(0xFF34D399) else Color(0xFFFCA5A5),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }) { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                when (section) {
                    AppSection.Dashboard -> DashboardScreen(
                        setupCompleted = setupCompleted,
                        activationCode = activationCode,
                        onActivationCodeChange = { activationCode = it },
                        syncEnabled = syncEnabled,
                        hasPermissions = hasSmsPermissions,
                        loggedIn = loggedIn,
                        onRequestCode = {
                            scope.launch {
                                AppContainer.repository(context).requestCode("mobile-device").fold(
                                    onSuccess = { code ->
                                        activationCode = code
                                        status = "Code generated and filled automatically."
                                    },
                                    onFailure = { status = "Code request failed: ${it.message}" }
                                )
                            }
                        },
                        onRegister = {
                            scope.launch {
                                AppContainer.repository(context).activate(activationCode).fold(
                                    onSuccess = {
                                        loggedIn = true
                                        prefs.setActivationCode(activationCode.trim())
                                        AppContainer.repository(context).resume().fold(
                                            onSuccess = {
                                                syncEnabled = true
                                                setupCompleted = true
                                                prefs.setSyncEnabled(true)
                                                prefs.setSetupCompleted(true)
                                                status = "Device registered. Sync enabled automatically."
                                            },
                                            onFailure = {
                                                setupCompleted = true
                                                prefs.setSetupCompleted(true)
                                                status = "Device registered, but sync enable failed: ${it.message}"
                                            }
                                        )
                                    },
                                    onFailure = { status = "Activation failed: ${it.message}" }
                                )
                            }
                        },
                        onToggleSync = { enabled ->
                            if (!loggedIn) {
                                status = "Register device first."
                                return@DashboardScreen
                            }
                            scope.launch {
                                val action = if (enabled) AppContainer.repository(context).resume() else AppContainer.repository(context).pause()
                                action.fold(
                                    onSuccess = {
                                        syncEnabled = enabled
                                        status = if (enabled) "Sync is ON." else "Sync is OFF."
                                        if (enabled && loggedIn && hasSmsPermissions && prefs.connectionVerified()) {
                                            setupCompleted = true
                                            prefs.setSetupCompleted(true)
                                        }
                                    },
                                    onFailure = { status = "Sync update failed: ${it.message}" }
                                )
                            }
                        }
                    )

                    AppSection.Settings -> SettingsScreen(
                        serverUrl = serverUrl,
                        onServerUrlChange = { serverUrl = it },
                        onTestConnection = {
                            prefs.setApiBaseUrl(serverUrl.trim())
                            scope.launch {
                                AppContainer.repository(context).testConnection().fold(
                                    onSuccess = { ok ->
                                        if (ok) {
                                            prefs.setConnectionVerified(true)
                                            status = "Connection verified."
                                        } else {
                                            status = "Connection failed."
                                        }
                                    },
                                    onFailure = { status = "Connection failed: ${it.message}" }
                                )
                            }
                        },
                        onGrantPermissions = {
                            smsPermissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                        },
                        hasPermissions = hasSmsPermissions
                    )

                    AppSection.Profile -> ProfileScreen(
                        activationCode = prefs.activationCode().orEmpty(),
                        loggedIn = loggedIn,
                        onLogoutReset = {
                            prefs.clearAll()
                            prefs.setApiBaseUrl(serverUrl)
                            loggedIn = false
                            syncEnabled = false
                            setupCompleted = false
                            activationCode = ""
                            section = AppSection.Dashboard
                            status = "Logged out. Register another device code."
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B5FFF), Color(0xFF58A0FF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TextPort", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Secure SMS Relay", color = Color.White.copy(alpha = 0.88f))
        }
    }
}

@Composable
private fun NavigationItem(label: String, active: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = active,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun DashboardScreen(
    setupCompleted: Boolean,
    activationCode: String,
    onActivationCodeChange: (String) -> Unit,
    syncEnabled: Boolean,
    hasPermissions: Boolean,
    loggedIn: Boolean,
    onRequestCode: () -> Unit,
    onRegister: () -> Unit,
    onToggleSync: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card("Synchronization") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sync incoming SMS")
                Switch(
                    checked = syncEnabled,
                    onCheckedChange = onToggleSync,
                    enabled = hasPermissions && loggedIn
                )
            }
        }

        if (!setupCompleted) {
            Card("Account Registration") {
                Text("Enter device code from admin panel.")
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = activationCode,
                    onValueChange = onActivationCodeChange,
                    label = { Text("Device Code") },
                    singleLine = true
                )
                TextButton(onClick = onRequestCode) {
                    Text("Request Code Automatically")
                }
                Button(onClick = onRegister) { Text("Register Device") }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onGrantPermissions: () -> Unit,
    hasPermissions: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card("API Connection") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text("API Base URL") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTestConnection) { Text("Test Connection") }
            }
        }

        Card("Permissions") {
            Text(if (hasPermissions) "SMS permission already granted." else "SMS permission required.")
            Button(onClick = onGrantPermissions) {
                Text(if (hasPermissions) "Grant Again" else "Grant Permission")
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    activationCode: String,
    loggedIn: Boolean,
    onLogoutReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card("Profile") {
            Text("Registered: ${if (loggedIn) "Yes" else "No"}")
            Text("Device Code: ${if (activationCode.isBlank()) "N/A" else activationCode}")
            Button(onClick = onLogoutReset) { Text("Log Out / Register Another Device") }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                content()
            }
        )
    }
}

private fun hasSmsPermissions(context: android.content.Context): Boolean {
    val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
    val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
    return receive == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
}
