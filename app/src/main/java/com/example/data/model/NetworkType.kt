package com.example.data.model

enum class NetworkType(val displayName: String) {
    WIFI("Wi-Fi"),
    MOBILE("Cellular Mobile"),
    ETHERNET("Ethernet"),
    BLUETOOTH("Bluetooth"),
    VPN("VPN"),
    OTHER("Other Network"),
    DISCONNECTED("Disconnected")
}
