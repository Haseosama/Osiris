package com.osiris.app.map

/**
 * The data layers wired up so far. Mirrors a subset of the "16 toggleable data layers"
 * described by the Osiris web app — more are added here as later phases land.
 */
enum class MapLayer(val label: String, val pollIntervalMs: Long, val defaultEnabled: Boolean) {
    FLIGHTS("Vols", 60_000L, defaultEnabled = true),
    EARTHQUAKES("Séismes", 5 * 60_000L, defaultEnabled = true),
    FIRES("Incendies", 10 * 60_000L, defaultEnabled = false),
    WEATHER("Météo sévère", 10 * 60_000L, defaultEnabled = false),
    CONFLICTS("Zones de conflit", 15 * 60_000L, defaultEnabled = false),
    MARITIME("Maritime", 20_000L, defaultEnabled = false),
    NEWS("Actu en direct", 30 * 60_000L, defaultEnabled = true),
    SATELLITES("Satellites", 60_000L, defaultEnabled = false),
    CYBER_ATTACKS("Cyberattaques", 15_000L, defaultEnabled = false),
    CCTV("CCTV", 5 * 60_000L, defaultEnabled = false),
    OSINT("OSINT Telegram", 2 * 60_000L, defaultEnabled = true),
}
