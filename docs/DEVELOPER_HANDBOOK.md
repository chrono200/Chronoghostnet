# ChronoGhostnet — Developer Handbook

**A plain-English technical guide to the project, its current behaviour, and how to develop it.**

- Project: ChronoGhostnet
- Repository: https://github.com/chrono200/Chronoghostnet
- Android application ID: `com.chrono.ghostnet`
- Main language: Kotlin
- UI: Jetpack Compose + Material 3
- Current source baseline: repository default branch, as inspected on 17 November 2026
- Current app version in Gradle: `0.1.0` (versionCode `1`)

> **Read this before assuming a feature exists.** ChronoGhostnet is currently an early Android networking foundation and radio-observation app. It is not yet a functioning offline messenger or a complete mesh network. This handbook deliberately separates what the current source code really does from the larger product vision.

---

# Part I — Understand the project

## 1. What is ChronoGhostnet?

ChronoGhostnet is intended to become a private, decentralised, device-to-device communication system.

The long-term idea is that two nearby phones should be able to discover one another and exchange information without depending on a central internet server. If the destination phone is not nearby, another participating device could eventually carry an encrypted message until it reaches the destination. This is commonly called **store-and-forward** networking.

Imagine three people:

- Phone A belongs to you.
- Phone B belongs to a friend.
- Phone C belongs to another friend who is currently far away.

In the future design, A could pass a message for C to B. B stores it and forwards it when C becomes reachable. A, B and C would form a small, decentralised network.

That is the vision. The current build is the foundation for exploring nearby radios and establishing a local device identity. It does not yet carry messages between those phones.

## 2. What the current APK actually does

The current source provides these working foundations:

1. **A native Android interface.** The app is written in Kotlin and uses Jetpack Compose to draw its screens.
2. **A local cryptographic identity.** On first launch, the app creates an elliptic-curve key pair in Android Keystore and displays a SHA-256 fingerprint derived from its public key.
3. **Bluetooth Low Energy discovery.** When Ghost Radar is started and the required permissions are granted, the app scans for BLE advertisements visible to Android and records the device address, available name and received signal strength.
4. **Wi-Fi Direct peer discovery request.** Starting Ghost Radar asks Android's Wi-Fi P2P service to discover nearby Wi-Fi Direct peers.
5. **Wi-Fi scan metadata.** Signal Observatory can request a Wi-Fi scan and display the scan results Android makes available: network name, access-point identifier, signal level, frequency and an approximate security label.
6. **A topology placeholder.** The Topology screen explains the difference between observed radio devices and authenticated links. It does not yet draw a real network graph.
7. **Peer candidate display.** The Peers screen lists BLE devices detected during the current app session. It does not yet save or trust them.
8. **Diagnostics.** The System screen displays the local fingerprint, current discovery status and future engineering milestones.
9. **Visual feedback.** The command-centre graphic pulses, and navigation between screens fades.

### What it does not do yet

The current APK does **not**:

- send or receive chat messages;
- transfer files;
- establish a Wi-Fi Direct group connection;
- open a TCP socket to another phone;
- authenticate another phone's public key;
- encrypt a message or establish an encrypted session;
- save peers or messages in a database;
- queue messages for later delivery;
- relay messages through another phone;
- implement multi-hop mesh routing;
- run a dependable always-on background network service.

The interface contains some future-facing labels and visual counters. Those should not be mistaken for implemented networking. For example, the Trusted and Queued metrics currently display fixed zeroes.

## 3. The important networking terms

| Term | Plain-English meaning |
|---|---|
| Node | One participating phone or device. |
| Peer | Another device that may communicate with this phone. |
| Discovery | Looking for nearby devices. Discovery does not create a connection. |
| BLE | Bluetooth Low Energy. A low-power radio system often used to broadcast small advertisements and discover nearby devices. |
| Advertisement | A small broadcast made by a BLE device. It is not the device's private messages or general Bluetooth traffic. |
| RSSI | Received Signal Strength Indicator. A rough measurement of how strong a radio signal appears to this phone, usually shown in dBm. |
| Wi-Fi Direct | An Android-supported way for compatible devices to form a direct Wi-Fi group without a conventional access point. |
| SSID | The human-readable name of a Wi-Fi network. |
| BSSID | The identifier of a particular Wi-Fi access point/radio interface. |
| Fingerprint | A short representation derived from a public key. It helps people compare identities. |
| Authentication | Proving that the device on the other end controls the private key corresponding to the public key it presents. |
| Encryption | Transforming information so that only someone with the correct key can read it. |
| Transport | The mechanism that actually carries data between devices, such as a socket over Wi-Fi Direct. |
| Store-and-forward | Keeping a message safely until the destination becomes reachable, then forwarding it. |
| TTL / hop limit | A limit that prevents an old message from circulating forever or being forwarded endlessly. |
| Mesh | A network in which devices can pass data through other devices rather than relying on one central router. |

**The key distinction:** discovering a phone is not connecting to it; connecting is not authenticating it; authenticating is not the same as encrypting; and none of those automatically creates a mesh network.

---

# Part II — How to use the current app

## 4. First launch and permissions

Android controls access to Bluetooth and nearby Wi-Fi information. ChronoGhostnet requests permissions because it cannot scan simply because the user installed it.

The current permission request includes:

- `ACCESS_FINE_LOCATION`: requested because Android versions and Wi-Fi scan APIs may require location permission to expose nearby Wi-Fi scan results.
- `BLUETOOTH_SCAN`: required on Android 12 and later to scan for nearby Bluetooth devices.
- `BLUETOOTH_CONNECT`: required on Android 12 and later for protected Bluetooth device information and operations.
- `NEARBY_WIFI_DEVICES`: requested on Android 13 and later for nearby Wi-Fi device operations.

The manifest also declares legacy Bluetooth permissions for Android versions through Android 11.

### What to do if scanning does not work

1. Open Android Settings → Apps → ChronoGhostnet → Permissions.
2. Allow the nearby-device/Bluetooth permissions shown by your Android version.
3. Allow Location when requested for Wi-Fi scan metadata.
4. Turn Bluetooth on before using Ghost Radar.
5. Turn Wi-Fi on for Wi-Fi-related discovery and scanning.
6. On Android versions that require it, turn the phone's Location service on.
7. Return to the app and try the scan again.

The exact labels vary by Android manufacturer and OS version. A permission being granted does not guarantee that Android will return fresh scan results.

## 5. The six navigation sections

### 5.1 Command

**Purpose:** the main landing screen.

It displays:

- the animated network illustration;
- the number of BLE candidates currently held in memory;
- the Signals metric;
- Trusted and Queued placeholder metrics;
- a shortcut to Ghost Radar;
- a shortcut to Topology.

The illustration is decorative. Its lines and dots are not a measured map of real devices. The screen explicitly reports that there are no authenticated links.

**Why it exists:** a future mesh application needs a central operational overview. This screen is the beginning of that dashboard, not proof that a mesh is active.

### 5.2 Radar — Ghost Radar

**Purpose:** discover BLE devices visible to the phone and request Wi-Fi Direct peer discovery.

When you press **Start Discovery**, the app:

1. Checks the runtime permissions it has assembled for the current Android version.
2. If permissions are missing, launches Android's multiple-permission request.
3. Sets its scanning state to active after the permission callback reports all requested permissions granted.
4. Starts a Bluetooth LE scan using Android's `BluetoothLeScanner`.
5. Requests Wi-Fi Direct peer discovery through `WifiP2pManager.discoverPeers()`, if the service and channel are available.
6. Updates the status text to report the outcome.

For each BLE scan result, the callback reads:

- **Address:** the Bluetooth device address exposed by Android.
- **Name:** the device name, if Android makes it available; otherwise it displays “Unknown device”.
- **RSSI:** the received signal strength in dBm.

If an address is already in the in-memory candidate list, its entry is updated. Otherwise, a new entry is added. This avoids adding a new row every time the same address is observed.

When you press **Stop Discovery**, the app stops its BLE scan and changes the status to Standby.

**Important current limitation:** the Stop action stops the BLE scan, but the present implementation does not explicitly call `WifiP2pManager.stopPeerDiscovery()`. Do not assume the Wi-Fi Direct discovery request is cancelled by that button. This should be corrected as part of the networking-service refactor.

The current scan callback handles individual scan results. It does not yet implement a full error/recovery strategy, scan batching, device filtering or a time-based expiry policy for old candidates.

### 5.3 Spectrum — Signal Observatory

**Purpose:** display nearby radio metadata that Android exposes to ordinary applications.

This is an observation tool, not a packet interceptor.

#### Wi-Fi section

Press **Scan Wi-Fi Spectrum**. The app:

1. Checks whether fine location permission is granted.
2. If not, asks Android for that permission.
3. Calls `WifiManager.startScan()` to request a scan.
4. Reads `WifiManager.scanResults` for the latest results Android makes available.
5. Listens for `SCAN_RESULTS_AVAILABLE_ACTION` so it can update the list when Android reports scan results.
6. Displays up to 60 entries.

Each Wi-Fi entry can show:

- **SSID:** network name. A hidden or unavailable name is displayed as “hidden network”.
- **BSSID:** access point identifier, if available.
- **RSSI:** signal level in dBm.
- **Frequency:** radio frequency in MHz, such as a 2.4 GHz or 5 GHz channel frequency.
- **Security:** a basic label inferred from the capability text Android provides.

The security label is a simple display classification. It is not a security audit and should not be treated as a complete evaluation of the access point.

#### Bluetooth LE section

This section shows the BLE candidates already collected by Ghost Radar:

- device name;
- address;
- RSSI.

It does not independently start a new Bluetooth scan. If the list is empty, start Ghost Radar first.

#### Why Wi-Fi scans may not refresh

Android can throttle scan requests to protect battery life and radio resources. The system may return cached results instead of performing a new scan immediately. The app therefore cannot promise that every button press produces a fresh radio survey.

Wi-Fi scan result access is also affected by Android version, permissions, Location service state, device manufacturer behaviour and whether Wi-Fi is enabled.

### 5.4 Topology

**Purpose:** establish the vocabulary and visual place for a future network map.

The current screen shows:

- the local node;
- the count of unverified signals;
- a legend for authenticated, observed and remembered devices;
- a warning that multi-hop routing is not implemented.

It does **not** calculate actual routes or draw connections between real peers. The current source has no authenticated links to display.

### 5.5 Peers — Peer Intelligence

**Purpose:** show BLE discovery candidates as potential peers.

It lists up to 30 candidates from the current in-memory BLE list. Each entry is labelled unverified because the app has not performed a cryptographic handshake with that device.

A nearby device is not automatically a ChronoGhostnet user. It may be a watch, speaker, tracker, laptop, another app, or an unrelated BLE advertiser.

The current list is temporary. It is not stored in a database and is not a trusted-device registry.

### 5.6 System — Diagnostics

**Purpose:** show the app's current local identity and high-level implementation status.

It displays:

- app core initialisation;
- the local Android Keystore identity type;
- BLE and Wi-Fi Direct adapter status labels;
- the full public-key fingerprint;
- the latest discovery status string;
- the next engineering milestones.

Some diagnostic labels describe code that has been wired up, not a successful end-to-end connection. For example, “Wi-Fi Direct peer discovery adapter” means the discovery request is present; it does not mean a Wi-Fi Direct connection has been established.

---

# Part III — How the code works

## 6. Project structure as it exists today

The current app is intentionally small. Most application logic and UI are currently in one file:

```text
Chronoghostnet/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/chrono/ghostnet/
│       │   └── MainActivity.kt
│       └── res/
│           ├── values/
│           ├── drawable/
│           └── mipmap-anydpi-v26/
├── .github/
│   └── workflows/
├── docs/
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

This is a **prototype layout**, not the final architecture. The planned modules for database, repositories, transport, routing and delivery have not all been created yet.

Keeping the prototype in one file made it quicker to build the first interface. As the networking logic grows, we should split it into smaller files so that scanning, identity, UI and transport can be tested independently.

## 7. Build configuration explained

The app module's `build.gradle.kts` defines how Android builds the application.

| Setting | Current value | What it means |
|---|---:|---|
| Namespace | `com.chrono.ghostnet` | Namespace used for generated Android code and resources. |
| Application ID | `com.chrono.ghostnet` | The package identity Android uses to install the app. |
| compileSdk | 36 | Android API level used to compile against. |
| minSdk | 26 | Oldest supported Android API level: Android 8.0. |
| targetSdk | 35 | Android behaviour/permission compatibility target. |
| versionCode | 1 | Internal integer used to compare app releases. |
| versionName | `0.1.0` | Human-readable app version. |
| Java compatibility | 17 | Java bytecode/toolchain compatibility. |
| Kotlin JVM target | 17 | Kotlin output targets JVM 17. |

### Main dependencies

- **AndroidX Activity Compose:** connects the Android Activity lifecycle to Compose content and activity-result permission launchers.
- **AndroidX Core KTX:** Kotlin-friendly Android utility APIs, including permission checks.
- **Lifecycle Runtime KTX / Compose:** lifecycle-aware runtime support for Android and Compose.
- **Compose UI:** the core declarative UI toolkit.
- **Compose Foundation:** layout, drawing, scrolling and foundational UI features.
- **Material 3:** buttons, navigation bars, surfaces, typography and theme components.
- **Material Icons Extended:** the icons used throughout the interface.
- **Compose UI Tooling:** development-time preview/debug support.

The current build does not declare Room, Hilt, WorkManager, Protocol Buffers or a networking/cryptography framework as dependencies. Those are possible future architectural choices, not present capabilities.

## 8. AndroidManifest.xml explained

The manifest tells Android what the app contains and which system capabilities it wants.

### Hardware features

- `android.hardware.bluetooth_le`: BLE support is optional. A device without BLE is not automatically blocked from installing the app.
- `android.hardware.wifi.direct`: Wi-Fi Direct support is optional.

The features are optional because the app should be able to open even on a device that lacks one of the radios. The relevant feature will simply be unavailable.

### Permissions

- `INTERNET`: declares internet access. The current core discovery features do not depend on a central internet server.
- `ACCESS_WIFI_STATE`: allows access to Wi-Fi state and scan information where Android permits it.
- `CHANGE_WIFI_STATE`: allows requesting certain Wi-Fi operations.
- `ACCESS_NETWORK_STATE`: allows checking network connectivity state.
- `BLUETOOTH` and `BLUETOOTH_ADMIN` with max SDK 30: legacy Bluetooth permissions for older Android versions.
- `BLUETOOTH_SCAN`: allows BLE scanning on newer Android versions.
- `BLUETOOTH_CONNECT`: allows protected Bluetooth interactions on newer Android versions.
- `NEARBY_WIFI_DEVICES`: declares nearby Wi-Fi device access on Android 13 and later.
- `ACCESS_FINE_LOCATION`: declared for Wi-Fi scan-result access on Android versions/devices that require it.

The application disables Android automatic backup with `allowBackup=false`. It declares `MainActivity` as the launcher activity.

**Permission principle:** ask only for permissions needed by a feature, explain why they are needed, and handle denial without crashing. Review permission behaviour whenever the target SDK or Android API changes.

## 9. MainActivity.kt — the central file

The current file contains the app's theme, local identity helper, data models, navigation, permission flow, radio scanning and screen composables.

### 9.1 Palette

`Palette` is an object holding the colours used across the interface:

- Void: main near-black background.
- Obsidian: raised dark surfaces.
- Panel: secondary panels.
- Border: subtle grid and divider colour.
- Cyan and Blue: primary technology accents.
- Amber: warning and unverified states.
- Green: positive/active states.
- Text and Muted: readable foreground and secondary text.

Centralising colours means we can change the app's visual identity without hunting through every screen.

### 9.2 LocalIdentity.fingerprint()

This function creates or retrieves the app's local signing key pair.

Step by step:

1. It chooses the Android Keystore alias `chrono-ghostnet-identity-v1`.
2. It opens the system Android Keystore.
3. It checks whether that alias already exists.
4. If not, it creates an elliptic-curve key pair using the `secp256r1` curve.
5. The key is configured for signing and verification with SHA-256.
6. The private key is kept by Android Keystore; the app retrieves the certificate's public key.
7. It hashes the encoded public key with SHA-256.
8. It formats the hash as uppercase hexadecimal byte pairs separated by colons.

**Why this matters:** a public-key fingerprint can act as a stable identifier for this app installation's key pair. Two people could compare fingerprints through a trusted channel in a future pairing flow.

**What it does not prove:** merely generating a key and displaying its fingerprint does not authenticate another device. The app has not yet exchanged challenges, verified signatures from a peer or established an encrypted session. The current key is also not yet used to sign messages.

If the app is uninstalled or its application data/Keystore entry is removed, the identity may be lost. A future release needs a deliberate identity backup/recovery policy; exporting private keys casually would undermine the security design.

### 9.3 RadioPeer data class

`RadioPeer` stores three values for a discovered BLE candidate:

- `address`: the Bluetooth address exposed by Android;
- `name`: the device name, or a fallback label;
- `rssi`: received signal strength.

This is an observation record, not a verified user profile. Bluetooth addresses can be randomised and may change. Names can be missing or spoofed.

### 9.4 WifiSignal data class

`WifiSignal` stores the visible Wi-Fi scan metadata:

- `ssid`: network name;
- `bssid`: access point identifier;
- `rssi`: signal level;
- `frequency`: frequency in MHz;
- `security`: simplified security label inferred from Android's capability string.

This is not a record of Wi-Fi packets or user traffic.

### 9.5 Screen enum

The `Screen` enum defines the six navigation destinations:

- COMMAND
- RADAR
- SPECTRUM
- TOPOLOGY
- PEERS
- SYSTEM

Each has a short label used by the bottom navigation bar. Using an enum avoids relying on fragile screen-name strings throughout the app.

### 9.6 MainActivity.onCreate()

Android launches the app through `MainActivity`. Its `onCreate()` method calls Compose's `setContent` and places the application inside `GhostTheme`.

This is the bridge between the Android operating system and the Compose UI.

### 9.7 GhostTheme()

This composable creates the Material 3 dark colour scheme using the project palette. It ensures buttons, surfaces and text use the same design language.

### 9.8 GhostnetApp()

This is the main application coordinator. It currently owns much of the app's live state and radio logic.

Important state variables:

- `screen`: currently selected navigation screen.
- `scanning`: whether the app considers Ghost Radar active.
- `status`: latest BLE/Wi-Fi Direct discovery status.
- `wifiStatus`: latest Wi-Fi scan status.
- `wifiSignals`: in-memory list of Wi-Fi scan entries.
- `candidates`: in-memory list of BLE candidates.

The lists use Compose snapshot state so that when entries change, the UI can redraw.

It obtains Android system services:

- `BluetoothManager` for Bluetooth access;
- `WifiManager` for Wi-Fi scan information;
- `WifiP2pManager` for Wi-Fi Direct discovery;
- a Wi-Fi P2P channel initialised with the app's main looper.

It also calculates the local identity fingerprint. If identity initialisation fails, the UI displays an error label instead of crashing during composition.

#### Runtime permission list

The app builds a permission array according to the Android version. It always includes fine location; on Android 12+ it adds Bluetooth scan/connect; on Android 13+ it adds nearby Wi-Fi devices.

The multiple-permission launcher receives a map of grant results. In the current implementation, scanning is enabled only when every requested permission is granted. Otherwise, the status reports that permissions were denied.

A future improvement should request permissions in a feature-specific way. For example, a user who only wants to view a previously captured identity should not need to grant radio scanning permissions.

#### BLE ScanCallback

Android invokes `onScanResult()` when a BLE result arrives.

The callback reads the device object, safely attempts to read its name, creates a `RadioPeer`, then searches the candidate list by address. Existing entries are replaced; new addresses are appended.

The callback is marked to suppress a lint warning about permissions because the surrounding permission flow is intended to ensure access. In production, permission checks and exception handling should still be robust against permissions being revoked while the app is running.

#### Wi-Fi BroadcastReceiver

Android broadcasts `WifiManager.SCAN_RESULTS_AVAILABLE_ACTION` when scan-result availability changes.

The receiver:

1. clears the previous Wi-Fi list;
2. reads up to 60 scan results;
3. extracts the capability string;
4. maps that string to a simple label (WPA3, WPA2, WPA, WEP or OPEN);
5. creates `WifiSignal` records;
6. updates the status message.

If reading the results fails, the UI is updated with a permission/location-related error.

The receiver is registered while the composable is active and unregistered when it is disposed. On Android 13+, it is registered as not exported so other apps cannot send it broadcasts.

#### Permission launchers

There are two activity-result launchers:

- The multiple-permission launcher handles the BLE/Wi-Fi Direct discovery permission set.
- The fine-location launcher handles the permission needed before requesting Wi-Fi scan metadata.

A permission launcher asks Android to show its system permission interface. It does not silently grant permission.

#### refreshWifi()

This local function handles the Spectrum screen's Wi-Fi refresh action.

- If fine location is missing, it launches the location permission request.
- If permission exists, it calls `startScan()`.
- It updates the status depending on whether Android accepted the request.
- It reads the latest available scan results and updates the UI list.

An accepted scan request is not the same as a guarantee that new results have already arrived. Android may throttle scans. The broadcast receiver also updates the list when results become available.

#### beginScan()

This checks whether all permissions in the app's assembled permission list are granted. If so, it enables scanning. Otherwise, it launches the permission request.

#### LaunchedEffect(scanning)

Compose's `LaunchedEffect` runs work when the `scanning` state changes.

When scanning becomes true:

1. the app attempts to start the BLE scanner;
2. it updates the status to indicate whether the BLE scanner is available;
3. if Wi-Fi Direct manager and channel are available, it requests peer discovery;
4. the Wi-Fi Direct callback reports success or a failure reason.

When scanning becomes false, the app attempts to stop the BLE scanner and sets the status to Standby.

This is a UI-lifecycle-driven prototype. A production networking engine should not depend on a screen remaining composed. It should move into a well-defined service/repository layer with explicit start, stop, cancellation and lifecycle rules.

#### DisposableEffect(Unit)

This effect registers the Wi-Fi scan-result receiver while the app composable is alive. When it is disposed, it stops the BLE scan defensively and unregisters the receiver.

This helps avoid leaving a receiver registered after the UI is removed. It is not an always-on background service.

#### Scaffold and navigation

The `Scaffold` creates the overall screen layout and bottom navigation bar.

For each `Screen` value, the code selects an icon and creates a navigation item. Selecting an item updates the `screen` state.

The screen content is displayed inside `AnimatedContent`, which fades between destinations. The animation is purely visual and does not change the network behaviour.

### 9.9 Header(fingerprint)

Displays the ChronoGhostnet brand, a local-node label and the first 14 characters of the local fingerprint. It is a compact identity indicator; it does not show a connected remote peer.

### 9.10 CommandScreen(count, onRadar, onTopology)

Displays the command-centre overview and operation shortcuts. The count is the current BLE candidate count. The Trusted and Queued values are hard-coded placeholders. The topology graphic is illustrative.

### 9.11 NetworkGraphic(modifier)

Uses Compose Canvas to draw a grid, a central point, surrounding points and connecting lines. An infinite transition changes the radius of the central glow to create a subtle pulse.

The nodes and lines are decorative. They are not positioned using actual signal distance or network topology data.

### 9.12 Metric(label, value, modifier)

A reusable small metric panel. It receives its label, value and layout modifier from the calling screen. It does not calculate metrics itself.

### 9.13 Operation(title, detail, icon, click)

A reusable clickable row with an icon, title, description and arrow. The caller supplies the action. This is used for the Command screen shortcuts.

### 9.14 RadarScreen(...)

Renders the Ghost Radar screen.

It receives scanning state, peer list, status and start/stop callbacks from the parent. It displays status tiles, the Start/Stop button and a list of detected BLE candidates.

The screen does not perform the Bluetooth scan itself. It presents state owned by `GhostnetApp()`.

### 9.15 SpectrumScreen(...)

Renders Signal Observatory. It receives BLE candidates and Wi-Fi observations from the parent, plus the Wi-Fi status and refresh callback.

It displays the Wi-Fi metadata list and BLE metadata list. The screen itself does not directly call Android radio APIs; the parent owns those operations.

### 9.16 StatusTile(...)

A reusable panel showing a feature name and its current status. It changes text colour based on whether the displayed value represents an active scan or standby.

### 9.17 TopologyScreen(count)

Displays the current placeholder topology view, the unverified signal count and the colour legend. It deliberately states that there are no authenticated direct links and that multi-hop routing is not implemented.

### 9.18 PeersScreen(peers)

Displays BLE discovery candidates. Each is labelled unverified because there is no identity handshake or trust flow yet.

### 9.19 SystemScreen(fingerprint, status)

Displays the local fingerprint, discovery status, implementation labels and future milestones. These labels are informational and do not constitute end-to-end tests of a connected peer.

### 9.20 Diagnostic(title, state, color)

A reusable row for a diagnostic category and its state. The caller supplies the text and colour.

---

# Part IV — Radio behaviour, privacy and security

## 10. What “real-time signal interception” means in this project

The word *intercept* can mean different things. ChronoGhostnet's Signal Observatory uses the safe, ordinary Android application interfaces for nearby discovery metadata.

### Bluetooth

The app scans for BLE advertisements that Android exposes to it. It can display an advertiser's available name, address and signal strength.

It does not capture arbitrary Bluetooth conversations, decode private device traffic, bypass pairing, or read another app's data.

### Wi-Fi

The app asks Android for nearby Wi-Fi scan results. It can display network beacon metadata such as SSID, BSSID, signal level, frequency and advertised security capabilities.

It does not capture raw Wi-Fi frames, recover passwords, decrypt traffic, read messages or inspect the contents of other people's connections.

### Why this boundary matters

Radio metadata is not the same as communication content. The app should remain transparent about what it observes, request the correct Android permissions, and never imply that it can see private messages or encrypted payloads.

## 11. Security model: what exists and what is still missing

### Implemented

- A local EC P-256 key pair is generated in Android Keystore.
- The private key is not exported by the fingerprint function.
- A SHA-256 fingerprint is derived from the public key.
- Discovered BLE devices are explicitly labelled unverified.

### Not implemented yet

- remote public-key exchange;
- proof-of-possession challenge/response;
- user confirmation of a peer fingerprint;
- trusted-peer storage;
- signed message envelopes;
- authenticated key agreement;
- session-key derivation;
- authenticated encryption of messages and files;
- replay protection;
- message expiry and deduplication;
- secure deletion and key rotation policy.

**Do not describe the current app as an encrypted messenger.** It has a local identity key foundation, but it does not yet encrypt communications because it does not yet send communications.

## 12. Threats the future design must consider

A serious private communication system must assume that nearby devices and radio observations can be misleading.

- **Impersonation:** a malicious device can advertise a familiar name. Names and MAC addresses are not proof of identity.
- **Replay:** an attacker may resend an old message or handshake. Future protocols need nonces, timestamps or counters and replay checks.
- **Tampering:** messages need authenticated integrity protection, not encryption alone.
- **Lost devices:** local keys and queued messages may be exposed if a device is compromised. Android Keystore protection helps but does not solve every device-compromise scenario.
- **Metadata leakage:** even encrypted messages can reveal who communicates, when, and how often. The design should minimise unnecessary metadata.
- **Resource exhaustion:** a malicious nearby device could produce many advertisements or connection requests. Scanning and parsing must be rate-limited.
- **Malicious files:** future file transfer must validate size, type, hashes and storage paths. Never execute received files automatically.
- **Battery drain:** continuous radio discovery can consume power. Discovery should use bounded scan windows and backoff.
- **False delivery claims:** a message should not be marked delivered until the destination acknowledges it under the protocol.

---

# Part V — The intended future architecture

## 13. The layers we should build

The following is the target architecture, not a description of code that already exists.

### 13.1 Presentation layer

Contains Compose screens and UI state. It should display information and send user actions to ViewModels. It should not directly own Bluetooth scanners or raw sockets.

Suggested areas:

- dashboard;
- radar;
- spectrum;
- topology;
- peers and trust;
- conversations;
- file transfers;
- diagnostics and settings.

### 13.2 Domain layer

Contains the rules of the application, independent of Android UI.

Examples:

- discover nearby candidates;
- pair with a peer;
- verify a peer identity;
- send a message;
- accept a message;
- queue a message for relay;
- expire an old message;
- calculate the next eligible relay.

### 13.3 Data layer

Stores and retrieves information. It should expose repository interfaces to the domain layer.

Likely future records:

- local identity metadata;
- peer public keys and trust state;
- conversations;
- message envelopes;
- delivery receipts;
- file-transfer metadata;
- relay queue entries.

Room is a possible Android database choice. It is not currently integrated.

### 13.4 Radio/discovery layer

Owns BLE scanning/advertising and Wi-Fi Direct discovery. It converts Android callbacks into app-level events.

It must handle permission revocation, radio disabled state, scan failures, device lifecycle and Android background limits.

### 13.5 Transport layer

Moves bytes between two connected devices. Wi-Fi Direct can provide a high-throughput local path, but it needs group negotiation and a real connection before a socket can exchange data.

A transport should expose clear operations such as connect, send, receive and close. It should not decide whether a peer is trusted.

### 13.6 Protocol and security layer

Defines how devices identify themselves, negotiate keys and encode messages. It should own:

- protocol version;
- peer handshake;
- signatures or authenticated key agreement;
- encryption and integrity;
- message IDs;
- timestamps and expiry;
- acknowledgements;
- replay and duplicate protection.

Use reviewed cryptographic protocols and libraries. Do not invent a home-made encryption algorithm.

### 13.7 Mesh routing and relay layer

Decides whether a message should be delivered directly or stored for later forwarding.

A relay needs:

- destination identity;
- message ID;
- creation and expiry times;
- hop limit;
- duplicate detection;
- storage limits;
- delivery acknowledgements;
- rules for deleting expired or successfully delivered messages.

A phone should not forward arbitrary content merely because it can see another phone. The user and protocol need explicit controls.

## 14. Suggested future source layout

As the project grows, a maintainable structure could look like this:

```text
app/src/main/java/com/chrono/ghostnet/
├── MainActivity.kt
├── navigation/
├── core/
│   ├── common/
│   ├── security/
│   ├── database/
│   └── design/
├── features/
│   ├── dashboard/
│   ├── radar/
│   ├── spectrum/
│   ├── topology/
│   ├── peers/
│   ├── messaging/
│   ├── transfers/
│   └── diagnostics/
├── network/
│   ├── discovery/
│   ├── bluetooth/
│   ├── wifidirect/
│   ├── transport/
│   └── protocol/
├── mesh/
│   ├── routing/
│   ├── relay/
│   └── delivery/
├── data/
│   ├── entities/
│   ├── dao/
│   └── repositories/
└── service/
```

Do not create empty folders just to make the project look sophisticated. Add a package when a real responsibility and implementation are ready to live there.

## 15. A realistic connection flow

The future direct-message path should work in stages:

1. **Discovery:** Phone A sees a compatible ChronoGhostnet advertisement from Phone B.
2. **Candidate state:** A records B as discovered, not trusted.
3. **Connection request:** the user chooses to connect.
4. **Transport establishment:** the devices negotiate a Wi-Fi Direct group or another supported direct transport.
5. **Protocol handshake:** both sides exchange protocol identity information and prove control of their private keys.
6. **Trust decision:** the user confirms the peer fingerprint, or a previously established trust policy permits it.
7. **Session setup:** the devices derive authenticated session keys using a reviewed protocol.
8. **Message exchange:** encrypted, authenticated message envelopes travel over the transport.
9. **Acknowledgement:** the receiver confirms acceptance; the sender updates delivery state.
10. **Disconnect:** resources are closed and the session is safely terminated.

A Wi-Fi Direct discovery result only completes part of step 1. It does not mean steps 3–10 have happened.

## 16. A realistic store-and-forward flow

Once direct messaging works, relay can be developed:

1. A creates a message addressed to C.
2. A encrypts the message for the intended recipient and creates a signed or authenticated envelope.
3. A connects to B and asks whether B is willing and permitted to carry the envelope.
4. B validates the envelope metadata and stores it under strict size and expiry limits.
5. When B later discovers C, B forwards the opaque envelope.
6. C verifies and decrypts it.
7. C returns a delivery acknowledgement.
8. The acknowledgement travels back when a route is available; relays remove their copies according to policy.

A relay should not need to read the message body. The protocol must prevent infinite loops, duplicates and unbounded storage.

## 17. Why “multi-hop mesh” is not a simple switch

Android Wi-Fi Direct is not a guarantee that every phone can maintain multiple simultaneous peer links and route traffic through them. Group-owner behaviour, device hardware, Android versions and vendor implementations can constrain what is possible.

Therefore:

- first implement and test one reliable direct connection;
- then test two devices exchanging data;
- then test a third device and a controlled relay experiment;
- measure connection stability, battery use and recovery after disconnection;
- only then describe a topology as a working multi-hop mesh.

Do not promise universal multi-hop behaviour until it has been demonstrated on the actual target devices.

---

# Part VI — Developing and testing

## 18. Build the project

### Requirements

- Android Studio with a compatible Android Gradle Plugin.
- JDK 17.
- Android SDK platform 36 and required build tools.
- Android device or emulator for basic UI testing.
- Physical Android devices for meaningful BLE and Wi-Fi Direct testing.

### Android Studio

1. Clone or download the repository.
2. Open the project root folder, the folder containing `settings.gradle.kts`.
3. Allow Gradle sync to finish.
4. Connect an Android phone with USB debugging enabled, or start an emulator.
5. Select the app run configuration.
6. Press Run.

To create a debug APK, use **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The APK is normally written under `app/build/outputs/apk/debug/`.

### Command line

From the project root, use the Gradle wrapper:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The expected debug APK path is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If the wrapper script is absent in a checkout, use Android Studio's Gradle tooling or restore the committed Gradle wrapper files before relying on the command above.

## 19. GitHub Actions APK build

The repository has a GitHub Actions workflow that builds the debug APK when changes are pushed to the configured branch.

Typical workflow:

1. Open the repository on GitHub.
2. Select **Actions**.
3. Open the newest run of the Android APK workflow.
4. Wait until the run reports success.
5. Find the artifact named `ChronoGhostnet-debug-apk`.
6. Download the artifact ZIP.
7. Extract the ZIP.
8. Install the APK inside it on the Android phone.

**Always choose the newest successful run.** An artifact from an older run contains the source as it existed when that run started. The artifact ZIP is not itself the installable APK; extract it first.

A successful CI build confirms that the project compiled in GitHub's build environment. It does not prove that BLE discovery, Wi-Fi Direct or Wi-Fi scan results work on every physical phone.

## 20. Physical-device test plan

Use two or more Android devices where possible. Record device model, Android version, permission state and test outcome.

### Test A — App launch

- Install the APK.
- Open the app.
- Confirm the command screen appears.
- Navigate through all six sections.
- Confirm there is no crash when returning between screens.

### Test B — Permission denial

- Deny Bluetooth permission.
- Try starting Ghost Radar.
- Confirm the app reports the issue instead of crashing.
- Grant permission in Settings and retry.
- Repeat for Location permission and Wi-Fi scan.

### Test C — BLE discovery

- Enable Bluetooth on two phones.
- Start Ghost Radar on the test phone.
- Make sure the second phone or a BLE test peripheral is advertising.
- Check whether a result appears.
- Compare the displayed RSSI as the devices move closer or farther apart.
- Stop discovery and confirm the BLE scan stops.

Note: two ordinary phones are not guaranteed to advertise a generic discoverable BLE identity continuously. A missing result does not automatically mean the scanner is broken.

### Test D — Wi-Fi scan metadata

- Enable Wi-Fi.
- Grant Location permission.
- Enable Location services if required by the device.
- Open Spectrum.
- Press Scan Wi-Fi Spectrum.
- Check whether nearby network names and metadata appear.
- Repeat after a short wait because Android may throttle scans.

Do not use a missing scan result as evidence that no Wi-Fi network exists.

### Test E — Wi-Fi Direct discovery

- Enable Wi-Fi on both devices.
- Start Ghost Radar.
- Inspect Android/system behaviour and logs for the Wi-Fi P2P discovery callback.
- Test on multiple device models.

The current UI does not yet render the actual Wi-Fi Direct peer list. A callback indicating discovery request success is not the same as a connected peer.

### Test F — Identity persistence

- Record the displayed fingerprint.
- Close and reopen the app.
- Confirm the fingerprint remains the same.
- Clear app data or reinstall only in a controlled test, then observe whether a new key is generated.

### Test G — Battery and lifecycle

- Start discovery.
- Switch away from the app.
- Lock the screen.
- Return to the app.
- Observe whether scanning continues, stops or is restricted by Android.
- Check battery usage.

The current prototype is not designed to guarantee background discovery. Android may stop or restrict work when the app is backgrounded.

## 21. Debugging guide

| Symptom | Possible explanation | What to check |
|---|---|---|
| Radar says permission denied | One or more requested permissions were denied | App permissions in Android Settings |
| Bluetooth list stays empty | No visible BLE advertisements, Bluetooth off, permission issue, or scanner unavailable | Bluetooth state, permissions, nearby advertising device, Logcat |
| Device name says Unknown device | Android did not expose a name for that advertiser | This is normal for some BLE advertisements |
| Wi-Fi list is empty | Location permission/service unavailable, Wi-Fi off, no results or manufacturer restrictions | Wi-Fi, Location, permissions, system scan settings |
| Wi-Fi refresh says throttled | Android did not accept a new scan request | Wait and use latest available results |
| Wi-Fi Direct discovery reports failure | P2P service/channel/radio state or device limitation | Android logs and physical-device test |
| Fingerprint shows initialization failure | Keystore/key generation or certificate retrieval failed | Logcat exception; device Keystore support |
| App compiles but radio feature fails | CI cannot validate hardware behaviour | Test on physical devices |
| App stops scanning after leaving screen | UI lifecycle and Android background restrictions | Move radio work into a properly designed service when needed |

Use Android Studio Logcat and filter by the application process. Do not log private keys, session keys, message bodies or other sensitive material.

## 22. Release discipline

Before distributing a build:

1. Update `versionCode` to a higher integer.
2. Update `versionName` to the release version.
3. Run the debug build and physical-device tests.
4. Review permission declarations and user-facing explanations.
5. Check that no secrets or signing credentials are committed.
6. Prepare a signed release build when release signing is configured.
7. Record the commit SHA used for the APK.
8. Keep a changelog describing what actually changed.

The current CI artifact is a **debug APK**. It is suitable for development/testing and sideloading, but it is not yet a properly configured public production release.

---

# Part VII — Engineering roadmap

## 23. Recommended implementation order

The order matters. Building chat screens before building a reliable connection and security layer would create a convincing-looking app that cannot safely communicate.

### Milestone 1 — Stabilise the foundation

- Split `MainActivity.kt` into focused files.
- Introduce a ViewModel for UI state.
- Move Bluetooth and Wi-Fi operations into dedicated managers/repositories.
- Handle Bluetooth adapter state and permission revocation.
- Stop Wi-Fi Direct peer discovery explicitly.
- Add scan failure callbacks, timeouts and candidate expiry.
- Correct app version labels so the UI, README and Gradle agree.
- Add basic automated tests for state and parsing.

**Completion test:** the UI remains stable while discovery starts, stops, fails and permissions change.

### Milestone 2 — Make discovery useful

- Add BLE advertising for ChronoGhostnet nodes.
- Define a small, versioned BLE service/advertisement format.
- Distinguish ChronoGhostnet-compatible nodes from unrelated BLE devices.
- Add a real Wi-Fi Direct peer list.
- Add explicit user-initiated connection requests.
- Test discovery across several physical Android devices.

**Completion test:** two installed ChronoGhostnet devices can reliably recognise one another as app candidates, without treating arbitrary BLE devices as peers.

### Milestone 3 — Establish direct transport

- Negotiate a Wi-Fi Direct group.
- Determine which device owns the group.
- Exchange connection parameters safely.
- Open a TCP socket only after the transport is ready.
- Define connection timeout, retry and shutdown behaviour.
- Add a minimal authenticated test exchange.

**Completion test:** two phones can establish a direct session and exchange a test payload repeatedly.

### Milestone 4 — Build identity and trust

- Exchange public identity keys.
- Design a signed challenge/response handshake.
- Verify peer possession of the private key.
- Show fingerprints for out-of-band comparison.
- Add user approval and revocation.
- Store trusted public keys safely.

**Completion test:** a device cannot become trusted just by copying a Bluetooth name or address.

### Milestone 5 — Secure messaging

- Choose a reviewed cryptographic protocol/library.
- Derive session keys with authenticated key agreement.
- Encrypt and authenticate message envelopes.
- Add message IDs, timestamps, expiry and acknowledgements.
- Persist conversations and messages.
- Handle reconnect and duplicate delivery.

**Completion test:** two trusted phones can exchange authenticated encrypted messages over a direct connection.

### Milestone 6 — File transfer

- Add transfer metadata and content hashes.
- Stream large files rather than loading them entirely into memory.
- Enforce file size limits and storage permissions.
- Support cancellation, progress, resume and integrity verification.
- Never automatically execute received files.

**Completion test:** a file arrives intact and the receiver can verify its hash.

### Milestone 7 — Store-and-forward

- Add a persistent relay queue.
- Define destination IDs, TTL and hop limit.
- Deduplicate messages.
- Add relay consent and storage limits.
- Deliver queued messages when the destination becomes reachable.
- Return acknowledgements and expire stale records.

**Completion test:** a message can travel from A through B to C in a controlled experiment, with delivery confirmation and no endless loops.

### Milestone 8 — Carefully validate mesh behaviour

- Test more than two devices.
- Measure connection stability and radio limitations.
- Experiment with group ownership and reconnect behaviour.
- Test routing under node movement and failure.
- Document which device combinations are supported.
- Do not claim general multi-hop support until repeatable tests prove it.

### Milestone 9 — Production readiness

- Threat model and security review.
- Data retention and deletion controls.
- Background execution and battery policy.
- Crash reporting with privacy safeguards.
- Signed release builds and upgrade strategy.
- Accessibility, localisation and device compatibility testing.

---

# Part VIII — Glossary and developer mindset

## 24. Android and Kotlin concepts in this project

**Activity:** an Android component representing an entry point to a user-facing screen. `MainActivity` hosts the app.

**Composable:** a Kotlin function annotated with `@Composable`. It describes UI. Compose redraws affected parts when observed state changes.

**State:** a value the UI observes. When state changes, Compose can recompose the UI that depends on it.

**remember:** keeps an object or value across recompositions while the composable remains in composition.

**rememberSaveable:** similar in spirit, but can save supported UI state across recreation. The current app primarily uses `remember`.

**LaunchedEffect:** runs a coroutine effect tied to the composable lifecycle and specified keys. In this project it reacts to the scanning state.

**DisposableEffect:** performs setup that must be cleaned up when the composable leaves composition. Here it registers and later unregisters a broadcast receiver.

**BroadcastReceiver:** Android component that receives broadcasts from the system or other permitted senders. Here it listens for Wi-Fi scan-result availability.

**Callback:** a function Android calls later when an operation produces a result or event.

**System service:** an Android-provided manager for a system capability, such as Bluetooth or Wi-Fi.

**Runtime permission:** a permission the user must grant while the app is running, not merely a declaration in the manifest.

**RSSI:** a received signal-strength measurement. It is approximate and affected by antennas, walls, interference and device orientation. It is not a precise distance measurement.

**Keystore:** Android's protected key-management system. It can keep private key material non-exportable and perform cryptographic operations using it.

**SHA-256:** a cryptographic hash function. In this app it produces a fingerprint from the encoded public key. A hash is not encryption.

**P-256 / secp256r1:** an elliptic curve used for public-key cryptography. The current key is configured for signing and verification.

## 25. Rules to remember while developing

1. **Never confuse discovery with trust.** A visible radio device is just a candidate.
2. **Never confuse a fingerprint with a handshake.** A local fingerprint alone proves nothing about a remote device.
3. **Never claim encryption until data is actually encrypted and authenticated.**
4. **Never claim delivery until the destination acknowledges it.**
5. **Never claim mesh routing until multi-device relay has been tested.**
6. **Do not put long-running radio work inside a screen composable.** Move it into lifecycle-aware components.
7. **Treat Android as the authority on permissions and radio availability.** Handle failure and revocation.
8. **Do not assume Wi-Fi scans are immediate.** Android controls scan frequency.
9. **Do not treat RSSI as exact distance.**
10. **Keep the app honest.** The interface and documentation must describe real capabilities, not planned ones.

## 26. Current project status at a glance

| Capability | Current status |
|---|---|
| Native Android Compose interface | Implemented |
| Animated screen transitions | Implemented |
| Pulsing command-centre illustration | Implemented |
| Custom launcher icon | Included in project resources |
| Local Keystore EC key pair | Implemented |
| SHA-256 public-key fingerprint | Implemented |
| BLE scan request and result list | Implemented as a prototype |
| Wi-Fi scan metadata display | Implemented, subject to Android restrictions |
| Wi-Fi Direct peer discovery request | Implemented |
| Actual Wi-Fi Direct connection | Not implemented |
| Real topology graph | Not implemented |
| Persistent peer registry | Not implemented |
| Peer authentication/trust | Not implemented |
| Encrypted message sessions | Not implemented |
| Chat messaging | Not implemented |
| File transfer | Not implemented |
| Persistent relay queue | Not implemented |
| Multi-hop routing | Not implemented |
| Reliable always-on background operation | Not implemented |

---

# Final note

ChronoGhostnet is a real project with a meaningful technical direction, but the current app is the first foundation—not the finished network.

The best way to build it is one verified layer at a time:

**discover → connect → authenticate → encrypt → exchange → acknowledge → relay.**

If each stage is implemented and tested before the next one is claimed, ChronoGhostnet can grow into something dependable rather than just looking like a sophisticated network dashboard.

This handbook describes the current source honestly and gives the developer a path for understanding and extending it.
