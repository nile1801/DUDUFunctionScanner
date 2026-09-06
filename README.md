# DUDU API Explorer

A clean-room, read-only discovery app for DUDU/FYT/SYU head units.

## Goal

Discover the APIs, components, Binder interfaces, FYT modules and DEX method/action strings that actually exist on the installed DUDU firmware. The app does not send CANBUS commands.

## Play Protect / permissions

This rewrite intentionally declares **no Android permissions**. In particular it does not request:

- `android.permission.READ_LOGS`
- `android.permission.QUERY_ALL_PACKAGES`
- Accessibility Service
- root
- storage permission

Package visibility is limited to an explicit `<queries>` allow-list of known DUDU/SYU packages.

## Discovery modes

1. **Package + Component Scan**
   - version / source path / signing certificate
   - exported activities, services, receivers and providers
   - component permissions and process names
   - resolver checks for sourced FYT actions

2. **FYT Toolkit Binder Probe**
   - binds read-only to `com.syu.ms/app.ToolkitService`
   - action: `com.syu.ms.toolkit`
   - inspects Binder descriptor
   - calls only `IRemoteToolkit.getRemoteModule(int)` (transaction 1) to enumerate module IDs 0..20
   - does **not** implement or call `IRemoteModule.cmd`

3. **FYT GET Probe (read-only)**
   - user selects one module and a small GET-code range
   - invokes only `IRemoteModule.get(...)` (transaction 2)
   - reports returned int/float/string arrays

4. **DEX API Scanner**
   - opens the installed APK in place via `ApplicationInfo.sourceDir`
   - reads `classes*.dex`
   - extracts vendor class/method signatures and interesting action/API strings
   - does not copy or modify vendor APKs

5. **TXT Report Export**
   - Android 10+: `Downloads/DUDUApiExplorer/`
   - no storage permission required

## Public-source evidence used for the scanner design

- `rsteckler/Climate100`: normal Android app binds to `com.syu.ms.toolkit`; its FYT IPC sources show `IRemoteToolkit.getRemoteModule` transaction 1 and `IRemoteModule.get` transaction 2.
- `vasyl91/FYT-Launcher-Mod`: reverse-engineered FYT/SYU code with `IRemoteToolkit`, `IRemoteModule`, CANBUS profiles and DUDU/FYT service architecture.
- `Vasilich/FYT_CustomService`: documents FYT ACC broadcasts `com.fyt.boot.ACCON` / `com.fyt.boot.ACCOFF` on modern FYT units.

The app intentionally treats public profiles as hints only. The installed DUDU firmware is the source of truth.
