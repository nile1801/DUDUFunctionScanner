# DUDU Function Scanner

Diagnostic Android app for DUDUOS/FYT head units (tested design target: DUDUOS 3.7 / UIS7870 family).

## What it records

- Accessibility click / long-click / selected events from other apps: package, class, text, content description, resource/view id and screen bounds.
- Hardware Android KeyEvent when the Accessibility service receives it (useful for steering-wheel/media keys that are mapped to Android key codes).
- FYT `com.syu.ms.toolkit` IPC updates from MAIN, BT and CANBUS modules, using the same read-only callback protocol already proven in `nile1801/FYTCanbusMonitor`.
- Nearby logcat lines when the unit grants `READ_LOGS` (or optional root mode is enabled), to look for Binder/service/cmd clues around the exact click timestamp.
- Exported DUDU/FYT/SYU activities, services, receivers and providers visible through Android PackageManager.
- Read-only probe of `content://com.syu.ms.provider/bt` when exported by the firmware.

## Important limitation

Android Accessibility can identify **which UI node was clicked**, but Android does **not** expose the Java/Kotlin method invoked inside another process. Therefore the app labels an exact function only when there is observable evidence (for example a logcat `cmd(...)`, service/Binder message, or a correlated FYT update). Otherwise it reports `CHƯA XÁC ĐỊNH` and shows evidence/candidates instead of inventing a function name.

For true method-level tracing of another app, the head unit would need a deeper instrumentation layer such as root + Frida/Xposed/JVMTI-compatible hooking. This first scanner build intentionally stays observational and does not inject CAN commands.

## DUDU/FYT evidence used

- DUDU's official CANBUS troubleshooting guide asks users to enable Protocol Print, press the affected button repeatedly and correlate the changing CANBUS data: https://forum.dudu-auto.com/d/846-how-to-give-correct-feedback-on-canbus-errors
- DUDUOS 3.7 forum debugging of climate-control issues also uses A/C operations plus CANBUS logs: https://forum.dudu-auto.com/d/2750-ac-app-temps-being-incorrectly-converted-37-beta
- Reverse engineering posted on the DUDU forum shows CarLink binding to `com.syu.ms` via action `com.syu.ms.toolkit`: https://forum.dudu-auto.com/d/2470-carlink-integration-with-dudu-music-widget/19
- Public FYT work demonstrates the same `com.syu.ms.toolkit` / `IRemoteModule` Binder protocol: https://github.com/PimpinPumpkin/FytRadio
- The MAIN/CANBUS module ids and subscription ranges are based on the existing FYTCanbusMonitor project that already works on the target unit.

## Setup on the head unit

1. Install the APK.
2. Open **Cài Accessibility** and enable **DUDU Function Scanner**.
3. Return to the scanner. FYT MAIN/BT/CANBUS monitoring starts automatically while the Accessibility service is enabled.
4. For richer logcat correlation (optional), from ADB run:

```bash
adb shell pm grant com.nile.dudufunctionscanner android.permission.READ_LOGS
```

5. Operate DUDU controls one at a time. The scanner groups UI/key input and FYT/logcat evidence by timestamp.
6. Use **Xuất TXT** and share the file for analysis.

## Safety

The scanner only observes/subscribes to FYT module updates. It does not call `cmd(...)`, transmit CAN frames, or change vehicle state.
