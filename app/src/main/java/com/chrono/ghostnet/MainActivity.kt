package com.chrono.ghostnet

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.Locale

private object Palette {
    val Void = Color(0xFF080B10)
    val Obsidian = Color(0xFF101720)
    val Panel = Color(0xFF15212D)
    val Border = Color(0xFF263747)
    val Cyan = Color(0xFF27D9F2)
    val Blue = Color(0xFF3978FF)
    val Amber = Color(0xFFFFB547)
    val Green = Color(0xFF35D69A)
    val Text = Color(0xFFE7F0F6)
    val Muted = Color(0xFF8294A5)
}

private object LocalIdentity {
    fun fingerprint(): String {
        val alias = "chrono-ghostnet-identity-v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }
        val key = store.getCertificate(alias)?.publicKey ?: error("Public key unavailable")
        return MessageDigest.getInstance("SHA-256").digest(key.encoded)
            .joinToString(":") { String.format(Locale.US, "%02X", it) }
    }
}

private data class RadioPeer(val address: String, val name: String, val rssi: Int)
private data class WifiSignal(val ssid: String, val bssid: String, val rssi: Int, val frequency: Int, val security: String)
private enum class Screen(val label: String) { COMMAND("Command"), RADAR("Radar"), SPECTRUM("Spectrum"), TOPOLOGY("Topology"), PEERS("Peers"), SYSTEM("System") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GhostTheme { GhostnetApp() } }
    }
}

@Composable
private fun GhostTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Palette.Cyan, secondary = Palette.Blue, tertiary = Palette.Amber,
        background = Palette.Void, surface = Palette.Obsidian,
        onBackground = Palette.Text, onSurface = Palette.Text
    ), content = content)
}

@SuppressLint("MissingPermission")
@Composable
private fun GhostnetApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.COMMAND) }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Standby") }
    var wifiStatus by remember { mutableStateOf("Awaiting scan") }
    val wifiSignals = remember { mutableStateListOf<WifiSignal>() }
    val candidates = remember { mutableStateListOf<RadioPeer>() }
    val bluetooth = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val wifi = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val wifiManager = remember { context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager }
    val wifiChannel = remember { wifiManager?.initialize(context, context.mainLooper, null) }
    val fingerprint = remember { runCatching { LocalIdentity.fingerprint() }.getOrElse { "IDENTITY INITIALIZATION FAILED" } }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.distinct().toTypedArray()
    }
    val scanCallback = remember {
        object : android.bluetooth.le.ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val d = result.device
                val peer = RadioPeer(d.address, runCatching { d.name }.getOrNull() ?: "Unknown device", result.rssi)
                val index = candidates.indexOfFirst { it.address == peer.address }
                if (index >= 0) candidates[index] = peer else candidates.add(peer)
            }
        }
    }
    val wifiReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    runCatching {
                        wifiSignals.clear()
                        wifi.scanResults.take(60).forEach { r ->
                            val caps = r.capabilities.orEmpty()
                            val security = when {
                                caps.contains("WPA3") -> "WPA3"
                                caps.contains("WPA2") -> "WPA2"
                                caps.contains("WPA") -> "WPA"
                                caps.contains("WEP") -> "WEP"
                                else -> "OPEN"
                            }
                            wifiSignals.add(WifiSignal(r.SSID.ifBlank { "<hidden network>" }, r.BSSID ?: "Unavailable", r.level, r.frequency, security))
                        }
                        wifiStatus = "Updated · " + wifiSignals.size + " networks observed"
                    }.onFailure { wifiStatus = "Scan results unavailable: permission or location setting required" }
                }
            }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) scanning = true
        else status = "Permissions denied — enable Nearby devices in Settings"
    }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching { wifi.startScan() }
            wifiStatus = "Scan requested · waiting for Android radio results"
        } else wifiStatus = "Location permission required by Android for nearby Wi-Fi scan results"
    }

    fun refreshWifi() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) wifiPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        else {
            val accepted = runCatching { wifi.startScan() }.getOrDefault(false)
            wifiStatus = if (accepted) "Scan requested · waiting for radio results" else "Scan throttled by Android · showing latest available results"
            runCatching {
                wifiSignals.clear()
                wifi.scanResults.take(60).forEach { r ->
                    val caps = r.capabilities.orEmpty()
                    val security = when {
                        caps.contains("WPA3") -> "WPA3"
                        caps.contains("WPA2") -> "WPA2"
                        caps.contains("WPA") -> "WPA"
                        caps.contains("WEP") -> "WEP"
                        else -> "OPEN"
                    }
                    wifiSignals.add(WifiSignal(r.SSID.ifBlank { "<hidden network>" }, r.BSSID ?: "Unavailable", r.level, r.frequency, security))
                }
            }.onFailure { wifiStatus = "Enable Location and grant permission to read Wi-Fi scan metadata" }
        }
    }

    fun beginScan() {
        val granted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) scanning = true else launcher.launch(permissions)
    }

    LaunchedEffect(scanning) {
        if (scanning) {
            val started = runCatching {
                bluetooth.adapter?.bluetoothLeScanner?.startScan(scanCallback)
                bluetooth.adapter?.bluetoothLeScanner != null
            }.getOrDefault(false)
            status = if (started) "BLE scan active" else "Bluetooth scanner unavailable"
            if (wifiManager != null && wifiChannel != null) {
                wifiManager.discoverPeers(wifiChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { status = "BLE + Wi-Fi Direct discovery requested" }
                    override fun onFailure(reason: Int) { status = "Wi-Fi Direct discovery error " + reason }
                })
            }
        } else {
            runCatching { bluetooth.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            status = "Standby"
        }
    }
    DisposableEffect(Unit) {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(wifiReceiver, filter)
        onDispose {
            runCatching { bluetooth.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            runCatching { context.unregisterReceiver(wifiReceiver) }
        }
    }

    Scaffold(
        containerColor = Palette.Void,
        bottomBar = {
            NavigationBar(containerColor = Palette.Obsidian, tonalElevation = 0.dp) {
                Screen.values().forEach { item ->
                    val icon = when (item) {
                        Screen.COMMAND -> Icons.Default.Home
                        Screen.RADAR -> Icons.Default.BluetoothSearching
                        Screen.SPECTRUM -> Icons.Default.GraphicEq
                        Screen.TOPOLOGY -> Icons.Default.Share
                        Screen.PEERS -> Icons.Default.Devices
                        Screen.SYSTEM -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = screen == item, onClick = { screen = item },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.Cyan, selectedTextColor = Palette.Cyan,
                            indicatorColor = Palette.Panel, unselectedIconColor = Palette.Muted,
                            unselectedTextColor = Palette.Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(fingerprint)
            AnimatedContent(targetState = screen, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "screen-transition") { activeScreen ->
            when (activeScreen) {
                Screen.COMMAND -> CommandScreen(candidates.size, { screen = Screen.RADAR }, { screen = Screen.TOPOLOGY })
                Screen.RADAR -> RadarScreen(scanning, candidates.toList(), status, ::beginScan) { scanning = false }
                Screen.SPECTRUM -> SpectrumScreen(candidates.toList(), wifiSignals.toList(), wifiStatus, ::refreshWifi)
                Screen.TOPOLOGY -> TopologyScreen(candidates.size)
                Screen.PEERS -> PeersScreen(candidates.toList())
                Screen.SYSTEM -> SystemScreen(fingerprint, status)
            }
            }
        }
    }
}

@Composable
private fun Header(fingerprint: String) {
    Row(Modifier.fillMaxWidth().background(Palette.Obsidian).padding(horizontal = 17.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Palette.Panel, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.WifiTethering, null, tint = Palette.Cyan)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("CHRONO", color = Palette.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("GHOSTNET", color = Palette.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("LOCAL NODE", color = Palette.Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(fingerprint.take(14), color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CommandScreen(count: Int, onRadar: () -> Unit, onTopology: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(17.dp)) {
        Text("NETWORK INTELLIGENCE", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Command centre", color = Palette.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("THE NETWORK HAS NO CENTRE.", color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(17.dp))
        Box(Modifier.fillMaxWidth().height(215.dp).background(Palette.Obsidian, RoundedCornerShape(18.dp))) {
            NetworkGraphic(Modifier.fillMaxSize())
            Column(Modifier.padding(15.dp)) {
                Text("NETWORK TOPOLOGY", color = Palette.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(if (count == 0) "Awaiting nearby signals" else count.toString() + " signals observed", color = Palette.Text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("NO AUTHENTICATED LINKS", Modifier.align(Alignment.BottomStart).padding(15.dp), color = Palette.Amber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Metric("SIGNALS", count.toString().padStart(2, '0'), Modifier.weight(1f))
            Metric("TRUSTED", "00", Modifier.weight(1f))
            Metric("QUEUED", "00", Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Text("OPERATIONS", color = Palette.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Operation("Initiate ghost radar", "BLE + Wi-Fi Direct discovery", Icons.Default.BluetoothSearching, onRadar)
        Spacer(Modifier.height(8.dp))
        Operation("Inspect network topology", "Observed devices vs trusted links", Icons.Default.Share, onTopology)
        Spacer(Modifier.weight(1f))
        Text("FOUNDATION 0.1.0  •  PRIVATE KEY STORED ON DEVICE", color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NetworkGraphic(modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "network-pulse").animateFloat(
        initialValue = 0.72f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse"
    )
    Canvas(modifier) {
        val center = Offset(size.width * .68f, size.height * .55f)
        val nodes = listOf(Offset(size.width*.2f,size.height*.35f), Offset(size.width*.46f,size.height*.22f), Offset(size.width*.87f,size.height*.28f), Offset(size.width*.85f,size.height*.78f), Offset(size.width*.38f,size.height*.8f))
        var x = 0f
        while (x < size.width) { drawLine(Palette.Border.copy(alpha=.3f), Offset(x,0f), Offset(x,size.height), 1f); x += 30.dp.toPx() }
        var y = 0f
        while (y < size.height) { drawLine(Palette.Border.copy(alpha=.3f), Offset(0f,y), Offset(size.width,y), 1f); y += 30.dp.toPx() }
        nodes.forEach { p ->
            drawLine(Palette.Cyan.copy(alpha=.3f), center, p, 1.2.dp.toPx(), cap=StrokeCap.Round)
            drawCircle(Palette.Cyan.copy(alpha=.12f), 9.dp.toPx(), p)
            drawCircle(Palette.Cyan.copy(alpha=.7f), 2.5.dp.toPx(), p)
        }
        drawCircle(Palette.Cyan.copy(alpha=.08f), (27.dp.toPx() * pulse), center)
        drawCircle(Palette.Cyan.copy(alpha=.28f), (16.dp.toPx() * pulse), center)
        drawCircle(Palette.Cyan, 5.dp.toPx(), center)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Palette.Obsidian, RoundedCornerShape(13.dp)).padding(12.dp)) {
        Text(label, color = Palette.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(value, color = Palette.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Operation(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Surface(onClick = click, color = Palette.Obsidian, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Palette.Cyan)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Palette.Text, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.ArrowForward, null, tint = Palette.Muted)
        }
    }
}

@Composable
private fun RadarScreen(scanning: Boolean, peers: List<RadioPeer>, status: String, start: () -> Unit, stop: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(17.dp)) {
        Text("SPECTRUM OPERATIONS", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Ghost radar", color = Palette.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Detect first. Authenticate before trusting.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatusTile("BLUETOOTH LE", if (scanning) "SCANNING" else "STANDBY", Modifier.weight(1f))
            StatusTile("WI-FI DIRECT", if (scanning) "DISCOVERY ON" else "STANDBY", Modifier.weight(1f))
        }
        Spacer(Modifier.height(13.dp))
        Button(onClick = if (scanning) stop else start, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = if (scanning) Palette.Panel else Palette.Cyan, contentColor = if (scanning) Palette.Text else Palette.Void), shape = RoundedCornerShape(12.dp)) {
            Text(if (scanning) "STOP DISCOVERY" else "START DISCOVERY", fontWeight = FontWeight.Bold)
        }
        Text(status, color = Palette.Amber, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top=8.dp))
        Spacer(Modifier.height(15.dp))
        Text("SIGNALS DETECTED  ·  " + peers.size.toString().padStart(2,'0'), color = Palette.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (peers.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Palette.Obsidian, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
                    Icon(Icons.Default.BluetoothSearching, null, tint = Palette.Muted, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (scanning) "Listening for nearby BLE devices" else "Radar is idle", color = Palette.Text, fontWeight = FontWeight.SemiBold)
                    Text("Nearby radio signals are not verified identities.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(peers.take(40), key = { it.address }) { peer ->
                    Column(Modifier.fillMaxWidth().background(Palette.Obsidian, RoundedCornerShape(11.dp)).padding(12.dp)) {
                        Text(peer.name, color = Palette.Text, fontWeight = FontWeight.SemiBold)
                        Text(peer.address, color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
                        Text("RSSI " + peer.rssi + " dBm  ·  UNVERIFIED", color = Palette.Amber, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}


@Composable
private fun SpectrumScreen(blePeers: List<RadioPeer>, wifiPeers: List<WifiSignal>, status: String, refresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(17.dp)) {
        Text("PASSIVE RADIO INTELLIGENCE", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Signal observatory", color = Palette.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Live nearby radio metadata. No payload interception, decryption or traffic capture.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("BLE DEVICES", blePeers.size.toString().padStart(2,'0'), Modifier.weight(1f))
            Metric("WI-FI NETWORKS", wifiPeers.size.toString().padStart(2,'0'), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = refresh, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Palette.Cyan, contentColor = Palette.Void)) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("SCAN WI-FI SPECTRUM", fontWeight = FontWeight.Bold)
        }
        Text(status, color = Palette.Amber, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top=6.dp))
        Spacer(Modifier.height(12.dp))
        Text("WI-FI BEACONS · SSID / BSSID / RSSI / FREQUENCY / SECURITY", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (wifiPeers.isEmpty()) {
            Text("No scan results yet. Grant Location permission and enable Location services. Android may throttle scans.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical=10.dp))
        } else {
            LazyColumn(Modifier.weight(1f, fill=false).heightIn(max=250.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(wifiPeers, key = { it.bssid }) { p ->
                    Column(Modifier.fillMaxWidth().background(Palette.Obsidian, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.ssid, color = Palette.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(p.rssi.toString() + " dBm", color = if (p.rssi > -65) Palette.Green else Palette.Amber, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(p.bssid + " · " + p.frequency + " MHz · " + p.security, color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("BLUETOOTH LE · DEVICE / ADDRESS / SIGNAL STRENGTH", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (blePeers.isEmpty()) Text("No BLE advertisements observed in the current radar session.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical=8.dp))
        else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(blePeers.take(20), key = { it.address }) { p ->
                    Row(Modifier.fillMaxWidth().background(Palette.Obsidian, RoundedCornerShape(9.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Palette.Text, fontWeight = FontWeight.SemiBold)
                            Text(p.address, color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(p.rssi.toString() + " dBm", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("LEGAL SCOPE · Public beacon metadata and your device's own radio diagnostics only. Encrypted communications remain untouched.", color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StatusTile(title: String, value: String, modifier: Modifier) {
    Column(modifier.background(Palette.Obsidian, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(title, color = Palette.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(value, color = if (value == "SCANNING" || value == "DISCOVERY ON") Palette.Green else Palette.Amber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TopologyScreen(count: Int) {
    Column(Modifier.fillMaxSize().padding(17.dp)) {
        Text("NETWORK MAP", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Topology", color = Palette.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Observed radio devices are not authenticated peers.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().weight(1f).background(Palette.Obsidian, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Share, null, tint = Palette.Cyan, modifier = Modifier.size(32.dp))
                Text("LOCAL NODE", color = Palette.Cyan, fontWeight = FontWeight.Bold)
                Text("No authenticated direct links", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
                Text(count.toString() + " unverified signals observed", color = Palette.Amber, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("GREEN  ·  Authenticated direct link", color = Palette.Green, style = MaterialTheme.typography.labelSmall)
        Text("AMBER  ·  Observed / unverified device", color = Palette.Amber, style = MaterialTheme.typography.labelSmall)
        Text("GREY  ·  Remembered peer currently offline", color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Text("Multi-hop mesh routing is not claimed in this build. Android Wi-Fi Direct group limits require physical-device validation.", color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PeersScreen(peers: List<RadioPeer>) {
    Column(Modifier.fillMaxSize().padding(17.dp)) {
        Text("IDENTITY REGISTRY", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Peer intelligence", color = Palette.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("A cryptographic fingerprint defines identity; aliases are labels.", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(15.dp))
        Text("DETECTED CANDIDATES  ·  " + peers.size, color = Palette.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (peers.isEmpty()) Text("No candidates detected. Start Ghost Radar to scan.", color = Palette.Muted, modifier = Modifier.padding(top=12.dp))
        peers.take(30).forEach { peer ->
            Column(Modifier.fillMaxWidth().padding(vertical=4.dp).background(Palette.Obsidian, RoundedCornerShape(11.dp)).padding(12.dp)) {
                Text(peer.name, color = Palette.Text, fontWeight = FontWeight.SemiBold)
                Text(peer.address, color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
                Text("UNVERIFIED · fingerprint handshake pending", color = Palette.Amber, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("No peer is trusted automatically based on Bluetooth visibility.", color = Palette.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SystemScreen(fingerprint: String, status: String) {
    Column(Modifier.fillMaxSize().padding(17.dp)) {
        Text("SYSTEM TELEMETRY", color = Palette.Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Diagnostics", color = Palette.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Foundation build · v0.1.0", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Diagnostic("APP CORE", "INITIALIZED", Palette.Green)
        Diagnostic("DEVICE IDENTITY", "ANDROID KEYSTORE · EC P-256", Palette.Green)
        Diagnostic("BLE DISCOVERY", "SCAN ADAPTER IMPLEMENTED", Palette.Amber)
        Diagnostic("WI-FI DIRECT", "PEER DISCOVERY ADAPTER", Palette.Amber)
        Spacer(Modifier.height(15.dp))
        Text("PUBLIC KEY FINGERPRINT", color = Palette.Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(fingerprint, color = Palette.Cyan, style = MaterialTheme.typography.labelSmall)
        Text("LAST EVENT  ·  " + status, color = Palette.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top=8.dp))
        Spacer(Modifier.height(16.dp))
        Text("NEXT ENGINEERING MILESTONES", color = Palette.Amber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        listOf("Authenticated key exchange + peer approval", "Encrypted session transport", "Wi-Fi Direct group negotiation + TCP", "Persistent messages and file transfer", "Store-and-forward routing + TTL").forEach {
            Text("•  " + it, color = Palette.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical=4.dp))
        }
    }
}

@Composable
private fun Diagnostic(title: String, state: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp).background(Palette.Obsidian, RoundedCornerShape(11.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(state, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}
