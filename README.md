# ChronoGhostnet
**THE NETWORK HAS NO CENTRE.**

ChronoGhostnet is an offline-first Android peer-networking project. Its long-term goal is private device-to-device messaging and file exchange with store-and-forward delivery.

## Foundation 0.1.0
- Native Kotlin + Jetpack Compose app.
- Dark intelligence command centre.
- Command, Ghost Radar, Topology, Peer Intelligence and Diagnostics screens.
- BLE scanning for nearby radio candidates.
- Wi-Fi Direct peer discovery request.
- EC P-256 device keypair in Android Keystore; SHA-256 public-key fingerprint.
- Runtime nearby-device permission request.

**This is not yet a mesh messenger.** It does not send messages or files. It does not yet implement authenticated peer handshakes, encrypted sessions, TCP transport, persistent relay queues or multi-hop routing. Nearby radio devices remain unverified.

## Decisions
- Networking: BLE discovery + Wi-Fi Direct transport path.
- Identity: cryptographic device identity in Android Keystore.
- Distribution: APK sideloading first.
- minSdk 26 / targetSdk 35 / compileSdk 36 / JDK 17.

## Download the APK
Every push to `main` triggers the GitHub Actions APK build. Open the repository's **Actions** tab, select the latest successful **Build ChronoGhostnet APK** run, and download the `ChronoGhostnet-debug-apk` artifact.

## Build locally
Open this repository in Android Studio, allow Gradle sync, connect a physical Android device, then run the app or choose **Build → Build APK(s)**.
