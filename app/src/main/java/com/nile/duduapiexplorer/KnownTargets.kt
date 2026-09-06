package com.nile.duduapiexplorer

object KnownTargets {
    val packages = listOf(
        "com.dudu.autoui",
        "com.syu.ms",
        "com.syu.canbus",
        "com.syu.carui",
        "com.syu.air",
        "com.syu.av",
        "com.syu.bt",
        "com.syu.calibration",
        "com.syu.carlink",
        "com.syu.carmark",
        "com.syu.carradio",
        "com.syu.cs",
        "com.syu.doublecamera",
        "com.syu.eq",
        "com.syu.filemanager",
        "com.syu.fourcamera2",
        "com.syu.mcukey",
        "com.syu.music",
        "com.syu.protocolupdate",
        "com.syu.ps",
        "com.syu.radio",
        "com.syu.rightcamera",
        "com.syu.screensaver",
        "com.syu.dvr",
        "com.syu.tpms",
        "com.syu.obd",
        "com.syu.steer"
    )

    val actions = listOf(
        "com.syu.ms.toolkit",
        "com.fyt.boot.ACCON",
        "com.fyt.boot.ACCOFF"
    )

    val modules = linkedMapOf(
        0 to "MAIN",
        1 to "RADIO",
        2 to "BT",
        3 to "DVD",
        4 to "SOUND",
        5 to "IPOD",
        6 to "TV",
        7 to "CANBUS",
        8 to "TPMS",
        9 to "DVR",
        10 to "STEER",
        11 to "CUSTOMER",
        12 to "OBD",
        13 to "TEST",
        14 to "CAN_UP",
        15 to "AMP",
        16 to "EMITTER",
        17 to "GSENSOR",
        18 to "GESTURE",
        19 to "SENSOR",
        20 to "ADAS"
    )

    const val TOOLKIT_ACTION = "com.syu.ms.toolkit"
    const val TOOLKIT_PACKAGE = "com.syu.ms"
    const val TOOLKIT_SERVICE = "app.ToolkitService"
    const val TOOLKIT_DESCRIPTOR = "com.syu.ipc.IRemoteToolkit"
    const val MODULE_DESCRIPTOR = "com.syu.ipc.IRemoteModule"
    const val TX_TOOLKIT_GET_REMOTE_MODULE = 1
    const val TX_MODULE_GET = 2
}
