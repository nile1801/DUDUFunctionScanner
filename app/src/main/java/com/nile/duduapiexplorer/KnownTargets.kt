package com.nile.duduapiexplorer

object KnownTargets {
    val packages = listOf(
        "com.syu.ms",
        "com.syu.canbus",
        "com.syu.air",
        "com.syu.carui",
        "com.syu.autosettings",
        "com.syu.mcukey",
        "com.syu.protocolupdate",
        "com.syu.cs",
        "com.syu.av",
        "com.syu.bt",
        "com.syu.carradio",
        "com.syu.carlink",
        "com.syu.fourcamera2",
        "com.syu.rightcamera",
        "com.syu.eq",
        "com.syu.music",
        "com.dudu.autoui",
        "com.dudu.voice",
        "com.dudu.settings"
    )

    val classKeywords = listOf(
        "api", "manager", "service", "toolkit", "canbus", "air", "climate",
        "vehicle", "car", "module", "control", "mcu", "radio", "bluetooth",
        "voice", "speech", "assistant", "dudu", "syu", "proxy", "remote"
    )

    val moduleNames = mapOf(
        0 to "MAIN", 1 to "RADIO", 2 to "BT", 3 to "DVD", 4 to "SOUND",
        5 to "IPOD", 6 to "TV", 7 to "CANBUS", 8 to "TPMS", 9 to "DVR",
        10 to "STEER", 11 to "CUSTOMER", 12 to "OBD", 13 to "TEST",
        14 to "CAN_UP", 15 to "AMP", 16 to "EMITTER", 17 to "GSENSOR",
        18 to "GESTURE", 19 to "SENSOR", 20 to "ADAS"
    )
}
