# Architecture

## Finalized decisions
- BLE discovery and signalling; Wi-Fi Direct is the intended high-throughput device-to-device transport.
- Cryptographic device identity: EC P-256 keypair stored in Android Keystore. SHA-256 public-key fingerprint is the stable identity. Nicknames are display metadata only.
- APK sideloading first.

## Current foundation
- Android native app in Kotlin and Jetpack Compose.
- BLE scan adapter and Wi-Fi Direct peer-discovery request.
- Local cryptographic identity generation and fingerprint.
- Five initial screens: Command, Radar, Topology, Peers, Diagnostics.

## Planned secure connection lifecycle
1. Discover candidate devices.
2. Exchange identity material and prove private-key possession.
3. User verifies fingerprint and approves peer.
4. Establish authenticated encrypted session.
5. Negotiate Wi-Fi Direct group and TCP transport.
6. Persist queued messages, enforce expiry and hop limits, and deduplicate before forwarding.

## Routing constraint
Wi-Fi Direct is not a guaranteed arbitrary multi-hop mesh API. Implement reliable direct sessions and store-and-forward first. Concurrent multi-hop forwarding requires real-device validation of radio concurrency, group-owner behavior and OS restrictions. The UI distinguishes observed devices from authenticated links.
