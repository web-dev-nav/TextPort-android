package com.brainandbolt.textport

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.brainandbolt.textport.data.AppContainer
import com.brainandbolt.textport.data.SecurePreferences
import com.brainandbolt.textport.data.SmsMessage
import com.brainandbolt.textport.data.SmsThread
import com.brainandbolt.textport.sms.SmsNotificationHelper
import com.brainandbolt.textport.ui.theme.TextPortTheme
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppSection { Dashboard, Settings, Profile }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SmsNotificationHelper.createChannel(this)
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
    var selectedThread by remember { mutableStateOf<SmsThread?>(null) }
    var smsThreads by remember { mutableStateOf<List<SmsThread>>(emptyList()) }
    var smsMessages by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var hasSmsPermissions by remember { mutableStateOf(hasSmsPermissions(context)) }
    var isDefaultSmsApp by remember { mutableStateOf(checkIsDefaultSmsApp(context)) }
    // Maps normalised address → latestTimestamp at the time the user opened it.
    // Any thread whose latestTimestamp <= this value is considered read by the user.
    var lastOpenedTimestamps by remember { mutableStateOf(mapOf<String, Long>()) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermissions = hasSmsPermissions(context)
        val ok = permissions[Manifest.permission.RECEIVE_SMS] == true &&
                permissions[Manifest.permission.READ_SMS] == true &&
                permissions[Manifest.permission.SEND_SMS] == true
        status = if (ok) "SMS permissions granted." else "SMS permissions denied."
        if (hasSmsPermissions && !prefs.connectionVerified()) showConnectionPrompt = true
    }
    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isDefaultSmsApp = true
            status = "TextPort set as default SMS app."
        } else {
            isDefaultSmsApp = checkIsDefaultSmsApp(context)
            status = if (isDefaultSmsApp) "TextPort set as default SMS app." else "Default SMS app not changed."
        }
    }

    // Load threads when on Dashboard; load messages when a thread is selected
    LaunchedEffect(section, hasSmsPermissions, selectedThread) {
        if (section == AppSection.Dashboard && hasSmsPermissions) {
            if (selectedThread == null) {
                smsThreads = withContext(Dispatchers.IO) { loadThreads(context) }
                    .applyReadOverrides(lastOpenedTimestamps)
            } else {
                withContext(Dispatchers.IO) { markThreadAsRead(context, selectedThread!!.threadId) }
                smsMessages = withContext(Dispatchers.IO) {
                    loadMessages(context, selectedThread!!.threadId, selectedThread!!.address)
                }
            }
        }
    }

    // Real-time updates: observe the SMS content provider and refresh immediately on any change
    val currentSelectedThread by rememberUpdatedState(selectedThread)
    val currentLastOpened by rememberUpdatedState(lastOpenedTimestamps)
    DisposableEffect(section, hasSmsPermissions) {
        if (section != AppSection.Dashboard || !hasSmsPermissions) {
            return@DisposableEffect onDispose {}
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch {
                    val thread = currentSelectedThread
                    if (thread == null) {
                        smsThreads = withContext(Dispatchers.IO) { loadThreads(context) }
                            .applyReadOverrides(currentLastOpened)
                    } else {
                        // Any DB change while in a conversation (sent or received) means
                        // the user is actively engaged — extend the read window to now so
                        // the unread badge never flickers back.
                        val key = normalizePhone(thread.address)
                        lastOpenedTimestamps = lastOpenedTimestamps + (key to System.currentTimeMillis())
                        smsMessages = withContext(Dispatchers.IO) {
                            loadMessages(context, thread.threadId, thread.address)
                        }
                    }
                }
            }
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        delay(1200)
        splashDone = true
        if (!prefs.permissionPrompted()) {
            prefs.setPermissionPrompted(true)
            val permissions = mutableListOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            smsPermissionLauncher.launch(permissions.toTypedArray())
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
                }) { Text("Test Connection") }
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
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("TextPort", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Navigation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NavigationItem("Dashboard", section == AppSection.Dashboard) {
                    section = AppSection.Dashboard
                    selectedThread = null
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
                        when {
                            selectedThread != null -> selectedThread!!.address
                            section == AppSection.Settings -> "Settings"
                            section == AppSection.Profile -> "Profile"
                            else -> "Dashboard"
                        }
                    )
                },
                navigationIcon = {
                    if (selectedThread != null) {
                        IconButton(onClick = {
                            selectedThread = null
                            scope.launch {
                                smsThreads = withContext(Dispatchers.IO) { loadThreads(context) }
                                    .applyReadOverrides(lastOpenedTimestamps)
                            }
                        }) {
                            Text("←", style = MaterialTheme.typography.titleLarge)
                        }
                    } else {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.titleLarge)
                        }
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
                when {
                    section == AppSection.Dashboard && selectedThread != null -> ConversationScreen(
                        address = selectedThread!!.address,
                        messages = smsMessages,
                        onSend = { message ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    sendSms(context, selectedThread!!.address, selectedThread!!.threadId, message)
                                }
                                // Extend the "read" window past the sent message's timestamp so
                                // applyReadOverrides keeps the badge clear after sending.
                                val key = normalizePhone(selectedThread!!.address)
                                lastOpenedTimestamps = lastOpenedTimestamps + (key to System.currentTimeMillis())
                                smsMessages = withContext(Dispatchers.IO) {
                                    loadMessages(context, selectedThread!!.threadId, selectedThread!!.address)
                                }
                            }
                        },
                        onMarkRead = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    markThreadAsRead(context, selectedThread!!.threadId, selectedThread!!.address)
                                }
                            }
                        }
                    )
                    section == AppSection.Dashboard -> DashboardScreen(
                        setupCompleted = setupCompleted,
                        activationCodeInput = activationCode,
                        onActivationCodeChange = { activationCode = it },
                        hasPermissions = hasSmsPermissions,
                        threads = smsThreads,
                        onThreadClick = { thread ->
                            val key = normalizePhone(thread.address)
                            // Record the timestamp we opened at — any message at or before this
                            // is considered read, even if markThreadAsRead fails (non-default app).
                            lastOpenedTimestamps = lastOpenedTimestamps + (key to thread.latestTimestamp)
                            // Instantly clear badge from the displayed list (no waiting for DB).
                            smsThreads = smsThreads.map { t ->
                                if (normalizePhone(t.address) == key)
                                    t.copy(hasUnread = false, unreadCount = 0)
                                else t
                            }
                            // Dismiss any system notification for this sender.
                            SmsNotificationHelper.cancelNotification(context, thread.address)
                            selectedThread = thread
                        },
                        onThreadDelete = { thread ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    deleteThread(context, thread.threadId, thread.address)
                                }
                                smsThreads = withContext(Dispatchers.IO) { loadThreads(context) }
                            }
                        },
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
                        }
                    )
                    section == AppSection.Settings -> SettingsScreen(
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
                        syncEnabled = syncEnabled,
                        onToggleSync = { enabled ->
                            if (!loggedIn) { status = "Register device first."; return@SettingsScreen }
                            scope.launch {
                                val action = if (enabled) AppContainer.repository(context).resume()
                                else AppContainer.repository(context).pause()
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
                        },
                        loggedIn = loggedIn,
                        onGrantPermissions = {
                            smsPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.SEND_SMS
                                )
                            )
                        },
                        hasPermissions = hasSmsPermissions,
                        isDefaultSmsApp = isDefaultSmsApp,
                        onSetDefaultSmsApp = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                context.getSystemService(RoleManager::class.java)
                                    .createRequestRoleIntent(RoleManager.ROLE_SMS)
                            } else {
                                @Suppress("DEPRECATION")
                                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                                }
                            }
                            defaultSmsLauncher.launch(intent)
                        },
                        onOpenDefaultAppsSettings = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        }
                    )
                    else -> ProfileScreen(
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
            .background(Brush.verticalGradient(listOf(Color(0xFF0B5FFF), Color(0xFF58A0FF)))),
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardScreen(
    setupCompleted: Boolean,
    activationCodeInput: String,
    onActivationCodeChange: (String) -> Unit,
    hasPermissions: Boolean,
    threads: List<SmsThread>,
    onThreadClick: (SmsThread) -> Unit,
    onThreadDelete: (SmsThread) -> Unit,
    onRequestCode: () -> Unit,
    onRegister: () -> Unit
) {
    var threadToDelete by remember { mutableStateOf<SmsThread?>(null) }

    if (threadToDelete != null) {
        AlertDialog(
            onDismissRequest = { threadToDelete = null },
            title = { Text("Delete Conversation") },
            text = { Text("Delete conversation with ${threadToDelete!!.address}? This cannot be undone.") },
            confirmButton = {
                Button(onClick = { onThreadDelete(threadToDelete!!); threadToDelete = null }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { threadToDelete = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Account Registration (only when not yet set up)
        if (!setupCompleted) {
            item {
                Card("Account Registration") {
                    Text("Enter device code from admin panel.")
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = activationCodeInput,
                        onValueChange = onActivationCodeChange,
                        label = { Text("Device Code") },
                        singleLine = true
                    )
                    TextButton(onClick = onRequestCode) { Text("Request Code Automatically") }
                    Button(onClick = onRegister) { Text("Register Device") }
                }
            }
        }

        // Messages header
        item {
            Text(
                "Messages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        when {
            !hasPermissions -> item {
                Text(
                    "SMS permission required to show messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            threads.isEmpty() -> item {
                Text(
                    "No messages yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                // Group threads into time buckets
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val todayStart = cal.timeInMillis
                val yesterdayStart = todayStart - 86_400_000L
                val weekStart = todayStart - 7 * 86_400_000L

                fun bucket(ts: Long) = when {
                    ts >= todayStart -> "Today"
                    ts >= yesterdayStart -> "Yesterday"
                    ts >= weekStart -> "This Week"
                    else -> "Earlier"
                }

                val order = listOf("Today", "Yesterday", "This Week", "Earlier")
                val grouped = threads.groupBy { bucket(it.latestTimestamp) }

                order.forEach { label ->
                    val group = grouped[label] ?: return@forEach
                    stickyHeader(key = "header_$label") {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                    items(group, key = { it.threadId }) { thread ->
                        ThreadItem(
                            thread = thread,
                            onClick = { onThreadClick(thread) },
                            onLongClick = { threadToDelete = thread }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadItem(thread: SmsThread, onClick: () -> Unit, onLongClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (thread.hasUnread)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Coloured avatar with initials
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(avatarColor(thread.address), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    addressInitials(thread.address),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        thread.address,
                        fontWeight = if (thread.hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatTimestamp(thread.latestTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (thread.hasUnread)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        thread.latestBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (thread.hasUnread)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (thread.hasUnread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    if (thread.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (thread.unreadCount > 99) "99+" else thread.unreadCount.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationScreen(
    address: String,
    messages: List<SmsMessage>,
    onSend: (String) -> Unit,
    onMarkRead: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
            onMarkRead()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F4F8))
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(messages) { index, msg ->
                val isSent = msg.type == Telephony.Sms.MESSAGE_TYPE_SENT

                // Date separator between messages on different days
                val showDate = index == 0 || !isSameDay(messages[index - 1].timestamp, msg.timestamp)
                if (showDate) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            formatDate(msg.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280),
                            modifier = Modifier
                                .background(Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                val sentShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                val receivedShape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (!isSent) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 2.dp)
                                .size(30.dp)
                                .background(avatarColor(address), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                addressInitials(address),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .widthIn(max = 270.dp)
                            .then(
                                if (isSent) {
                                    Modifier.background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                                        ),
                                        shape = sentShape
                                    )
                                } else {
                                    Modifier.background(Color.White, receivedShape)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            msg.body,
                            color = if (isSent) Color.White else Color(0xFF111827),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            formatTimestamp(msg.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSent) Color.White.copy(alpha = 0.65f) else Color(0xFF9CA3AF),
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Message…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (messageText.isNotBlank())
                                listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                            else
                                listOf(Color(0xFFCBD5E1), Color(0xFFCBD5E1))
                        ),
                        shape = CircleShape
                    )
                    .clickable(enabled = messageText.isNotBlank()) {
                        onSend(messageText.trim())
                        messageText = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "➤",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    syncEnabled: Boolean,
    onToggleSync: (Boolean) -> Unit,
    loggedIn: Boolean,
    onGrantPermissions: () -> Unit,
    hasPermissions: Boolean,
    isDefaultSmsApp: Boolean,
    onSetDefaultSmsApp: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

        Card("API Connection") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text("API Base URL") },
                singleLine = true
            )
            Button(onClick = onTestConnection) { Text("Test Connection") }
        }

        Card("Permissions") {
            Text(if (hasPermissions) "SMS permissions granted." else "SMS permissions required.")
            Button(onClick = onGrantPermissions) {
                Text(if (hasPermissions) "Grant Again" else "Grant Permissions")
            }
        }

        Card("Default SMS App") {
            Text(
                if (isDefaultSmsApp)
                    "TextPort is the default SMS app. Incoming SMS will be written to the system inbox."
                else
                    "Set TextPort as the default SMS app to enable system inbox write-back. Your native SMS app will still show all messages."
            )
            Button(onClick = onSetDefaultSmsApp, enabled = !isDefaultSmsApp) {
                Text(if (isDefaultSmsApp) "Already Default" else "Set as Default SMS App")
            }
            if (!isDefaultSmsApp) {
                TextButton(onClick = onOpenDefaultAppsSettings) {
                    Text("Open System Default Apps Settings")
                }
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

// ── Data helpers ──────────────────────────────────────────────────────────────

// Maps normalised address → all thread IDs collected during loadThreads.
// loadMessages uses this to pull every message across all threads for a number.
private val addressToThreadIds = mutableMapOf<String, Set<Long>>()

private fun loadThreads(context: Context): List<SmsThread> {
    // Key by normalised phone number so the same contact is always one entry,
    // regardless of thread_id churn or number-format differences (+1555… vs 555…).
    val byAddress = linkedMapOf<String, SmsThread>()          // norm address → latest thread info
    val unreadMap = mutableMapOf<String, Int>()               // norm address → unread count
    val threadIdMap = mutableMapOf<String, MutableSet<Long>>()// norm address → all thread ids

    val cursor = context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
        null, null,
        "${Telephony.Sms.DATE} DESC"
    ) ?: return emptyList()

    cursor.use {
        val threadIdIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
        val addrIdx     = it.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx     = it.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx     = it.getColumnIndex(Telephony.Sms.DATE)
        val readIdx     = it.getColumnIndex(Telephony.Sms.READ)
        val typeIdx     = it.getColumnIndex(Telephony.Sms.TYPE)

        while (it.moveToNext()) {
            val rawAddress  = it.getString(addrIdx) ?: continue
            val normAddress = normalizePhone(rawAddress)
            val threadId    = it.getLong(threadIdIdx)

            threadIdMap.getOrPut(normAddress) { mutableSetOf() }.add(threadId)

            // Only inbox messages (type=1) that haven't been read count as unread
            if (it.getInt(readIdx) == 0 &&
                it.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                unreadMap[normAddress] = (unreadMap[normAddress] ?: 0) + 1
            }
            // First row per address = latest message (DATE DESC)
            if (!byAddress.containsKey(normAddress)) {
                byAddress[normAddress] = SmsThread(
                    threadId        = threadId,
                    address         = rawAddress,
                    latestBody      = it.getString(bodyIdx) ?: "",
                    latestTimestamp = it.getLong(dateIdx)
                )
            }
        }
    }

    // Publish the address→threadIds map so loadMessages can find all threads for a number
    addressToThreadIds.clear()
    addressToThreadIds.putAll(threadIdMap)

    return byAddress.values.map { thread ->
        val unread = unreadMap[normalizePhone(thread.address)] ?: 0
        thread.copy(hasUnread = unread > 0, unreadCount = unread)
    }
}

private fun loadMessages(context: Context, threadId: Long, address: String = ""): List<SmsMessage> {
    // Collect every thread id that belongs to this address (covers number-format variants
    // and cases where the thread was deleted/recreated under a new id).
    val normAddress = normalizePhone(address)
    val threadIds = if (normAddress.isNotBlank())
        addressToThreadIds[normAddress] ?: setOf(threadId)
    else
        setOf(threadId)

    val placeholders = threadIds.joinToString(",") { "?" }
    val args = threadIds.map { it.toString() }.toTypedArray()

    val messages = mutableListOf<SmsMessage>()
    val cursor = context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
        "${Telephony.Sms.THREAD_ID} IN ($placeholders)",
        args,
        "${Telephony.Sms.DATE} ASC"
    ) ?: return emptyList()

    cursor.use {
        val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
        val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
        while (it.moveToNext()) {
            messages.add(
                SmsMessage(
                    address   = it.getString(addrIdx) ?: "",
                    body      = it.getString(bodyIdx) ?: "",
                    timestamp = it.getLong(dateIdx),
                    type      = it.getInt(typeIdx)
                )
            )
        }
    }
    return messages.sortedBy { it.timestamp }
}

private fun sendSms(context: Context, phoneNumber: String, threadId: Long, message: String) {
    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
    }
    smsManager?.sendTextMessage(phoneNumber, null, message, null, null)

    // Write the sent message into the same thread so it appears in the conversation
    val values = ContentValues().apply {
        put(Telephony.Sms.ADDRESS, phoneNumber)
        put(Telephony.Sms.BODY, message)
        put(Telephony.Sms.DATE, System.currentTimeMillis())
        put(Telephony.Sms.READ, 1)
        put(Telephony.Sms.THREAD_ID, threadId)
        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
    }
    context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
}

private fun markThreadAsRead(context: Context, threadId: Long, address: String = "") {
    val ids = resolveThreadIds(threadId, address)
    val placeholders = ids.joinToString(",") { "?" }
    val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
    context.contentResolver.update(
        Telephony.Sms.CONTENT_URI,
        values,
        "${Telephony.Sms.THREAD_ID} IN ($placeholders) AND ${Telephony.Sms.READ} = 0",
        ids.map { it.toString() }.toTypedArray()
    )
}

private fun deleteThread(context: Context, threadId: Long, address: String = "") {
    val ids = resolveThreadIds(threadId, address)
    val placeholders = ids.joinToString(",") { "?" }
    context.contentResolver.delete(
        Telephony.Sms.CONTENT_URI,
        "${Telephony.Sms.THREAD_ID} IN ($placeholders)",
        ids.map { it.toString() }.toTypedArray()
    )
}

/** Returns all known thread ids for an address, falling back to the single provided id. */
private fun resolveThreadIds(threadId: Long, address: String): Set<Long> {
    val norm = normalizePhone(address)
    return if (norm.isNotBlank()) addressToThreadIds[norm] ?: setOf(threadId)
    else setOf(threadId)
}

/** Strips non-digit chars and keeps last 10 digits for reliable cross-format comparison. */
private fun normalizePhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.length >= 10) digits.takeLast(10) else digits.ifBlank { phone.trim().lowercase() }
}

private fun avatarColor(address: String): Color {
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899),
        Color(0xFF14B8A6), Color(0xFFF59E0B), Color(0xFF10B981),
        Color(0xFF3B82F6), Color(0xFFEF4444)
    )
    return palette[Math.abs(address.hashCode()) % palette.size]
}

private fun addressInitials(address: String): String {
    val words = address.trim().split(Regex("\\s+"))
    return if (words.size >= 2) {
        "${words[0].firstOrNull() ?: ""}${words[1].firstOrNull() ?: ""}".uppercase()
    } else {
        address.filter { it.isLetter() || it.isDigit() }.take(2).uppercase().ifBlank { "?" }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = t1
    val y1 = cal.get(java.util.Calendar.YEAR); val d1 = cal.get(java.util.Calendar.DAY_OF_YEAR)
    cal.timeInMillis = t2
    return y1 == cal.get(java.util.Calendar.YEAR) && d1 == cal.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun hasSmsPermissions(context: Context): Boolean {
    val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
    val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
    val send = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
    return receive == PackageManager.PERMISSION_GRANTED &&
            read == PackageManager.PERMISSION_GRANTED &&
            send == PackageManager.PERMISSION_GRANTED
}

private fun checkIsDefaultSmsApp(context: Context): Boolean =
    Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

/**
 * Clears the unread badge for any thread whose latestTimestamp is at or before
 * the timestamp stored when the user last opened that conversation.
 * This ensures the badge stays gone even when loadThreads() is called again
 * (e.g. back-button reload, ContentObserver) before markThreadAsRead() persists.
 */
private fun List<SmsThread>.applyReadOverrides(lastOpened: Map<String, Long>): List<SmsThread> =
    map { t ->
        val lastRead = lastOpened[normalizePhone(t.address)] ?: -1L
        if (lastRead >= t.latestTimestamp) t.copy(hasUnread = false, unreadCount = 0) else t
    }
