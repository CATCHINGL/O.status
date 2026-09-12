# O.status

O.status is a lightweight Android status indicator overlay focused on showing essential device status in a compact, minimal form.

**Current release: 1.0.0**

## Features

- Compact Duo status indicator
- Battery level ring with charging, low-battery, power-saving and full-charge states
- Wi-Fi signal display
- Default Data SIM cellular signal display
- 4G / 5G display, including Android 5G NSA display information
- Dual-SIM details
- Real-time network download and upload speed in Details
- Automatic Black / White indicator following Android Light / Dark Mode
- Manual Black / White colour override
- Position & Size adjustment
- Do Not Disturb avoidance
- Start on boot
- One-shot recovery after the app task is removed

## Network behaviour

The four cellular dots in the Duo represent the **Default Data SIM**. The center network label and the cellular dots therefore refer to the same SIM. Details shows each active SIM separately.

5G display uses Android telephony display information so supported 5G NSA connections can be shown as 5G rather than being treated only as LTE.

## Permissions

- **Display over other apps** — draws the status indicator
- **Phone status** — reads cellular/network state
- **Do Not Disturb access** — temporarily avoids the DND status area
- **Network state / Wi-Fi state** — reads current connectivity
- **Foreground service** — keeps the user-enabled indicator running
- **Boot completed** — restores the indicator after restart when Start on Boot is enabled

O.status does **not** require Location, Accessibility or Usage Access.

## Privacy

O.status performs its status calculations locally on the device. The 1.0.0 source tree contains no analytics SDK, advertising SDK, account system, API key or remote telemetry endpoint.

## Build

- Android Studio
- JDK 17
- minSdk 29
- targetSdk 35
- compileSdk 35

Open the project folder in Android Studio, allow Gradle to sync, then build/run the `app` module.

> The repository currently does not include the Gradle Wrapper binaries.

## Package

`com.catch7ng.ostatus`

## License

**Source Code Available for Reference — All Rights Reserved**

The O.status source code is publicly available for viewing, study, and
educational reference only.

No permission is granted to copy, redistribute, modify, publish, create
derivative works, incorporate the source into another project, or use it
commercially without prior written permission from the copyright holder.

See `LICENSE` for the full terms.

## Author

**Jason Leung / CATCH7NG.L**

GitHub: https://github.com/CATCHINGL

## Copyright

© 2026 CATCH7NG.L · All Rights Reserved
