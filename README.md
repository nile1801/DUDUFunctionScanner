# DUDU API Explorer

A read-only Android diagnostic app for DUDU/FYT head units.

## Goal
Discover usable DUDU/FYT integration surfaces without controlling the vehicle:
- explicit DUDU/SYU package inventory
- exported activities/services/receivers/providers
- readable vendor DEX class names
- public/protected methods and fields from candidate API/service/control classes
- FYT `com.syu.ms.toolkit` Binder connectivity
- read-only enumeration of FYT remote module Binder descriptors for module IDs 0..20

## Safety / Play Protect-oriented design
This rewrite intentionally has:
- no Accessibility Service
- no `READ_LOGS`
- no root commands
- no `QUERY_ALL_PACKAGES`
- no storage permission
- no network permission
- no background service
- no CANBUS `cmd()` calls

The app only uses explicit `<queries>` package visibility, PackageManager, DEX reflection, Binder metadata and MediaStore report export.

## Public reverse-engineering references used for architecture
- AxesOfEvil/FYTCanbusMonitor: FYT toolkit binding, module mapping, `IRemoteToolkit.getRemoteModule()`
- PimpinPumpkin/FytRadio / FytBt: normal third-party FYT/SYU IPC architecture
- vasyl91/FYT-Launcher-Mod: FYT/SYU package and CANBUS implementation naming
- Vasilich/FYT_CustomService: FYT Android service/device architecture reference
- rsteckler/Climate100: FYT CANBUS control architecture reference (control calls are deliberately NOT included here)

## Output
Reports are written to `Download/DUDUApiExplorer/` on Android 10+.
