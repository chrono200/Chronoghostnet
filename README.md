# ChronoGhostnet
**THE NETWORK HAS NO CENTRE.**

ChronoGhostnet is an offline-first Android radio-intelligence and peer-networking foundation. Its long-term goal is private device-to-device messaging and file exchange with store-and-forward delivery.

## Foundation 0.2.0
- Native Kotlin + Jetpack Compose interface with subtle animated screen transitions and a pulsing network node.
- Custom ChronoGhostnet adaptive launcher icon.
- Command, Ghost Radar, Signal Observatory, Topology, Peer Intelligence and Diagnostics screens.
- BLE discovery of nearby advertising devices and visible signal metadata.
- Wi-Fi scan metadata: SSID, BSSID, RSSI, frequency and advertised security mode.
- Wi-Fi Direct peer discovery request.
- EC P-256 device keypair in Android Keystore; SHA-256 public-key fingerprint.
- Runtime permissions with an explicit Location permission for Android Wi-Fi scan results.

## Signal Observatory — legal scope
The observatory displays broadcast/discovery metadata that Android exposes to ordinary apps. It does **not** capture or decode private Wi-Fi/Bluetooth payloads, decrypt traffic, bypass encryption, or intercept communications. Android controls scan frequency and may return cached or throttled Wi-Fi results; refresh is not guaranteed to produce a new scan immediately. Bluetooth observations are limited to BLE advertisements visible to the app.

## What this build does not do
This is not yet a mesh messenger. It does not send messages or files. It does not yet implement authenticated peer handshakes, encrypted sessions, TCP transport, persistent relay queues or multi-hop routing. Nearby radio devices remain unverified.

## Decisions
- Networking: BLE discovery + Wi-Fi Direct transport path.
- Identity: cryptographic device identity in Android Keystore.
- Distribution: APK sideloading first.
- minSdk 26 / targetSdk 35 / compileSdk 36 / JDK 17.

## Download the APK
Every push to `main` triggers the GitHub Actions APK build. Open the repository's **Actions** tab, select the latest successful **Build ChronoGhostnet APK** run, and download the `ChronoGhostnet-debug-apk` artifact.

## Build locally
Open this repository in Android Studio, allow Gradle sync, connect a physical Android device, then run the app or choose **Build → Build APK(s)**.


## Documentation

The detailed developer guide is available in [`docs/DEVELOPER_HANDBOOK.md`](docs/DEVELOPER_HANDBOOK.md). It explains the current app screen by screen, the Kotlin/Android implementation, permissions, radio behaviour, security boundaries, build and testing steps, and the roadmap. It also clearly separates implemented features from planned features.
