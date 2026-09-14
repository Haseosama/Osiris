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
    // TomTom's free-tier daily quota is the limiting factor here, not freshness: the backend
    // fans out to 8 French metro-hub requests per poll (see /api/traffic — a single France-wide
    // request 400s, TomTom caps bbox area at 10,000km²), so a short interval burns through the
    // quota fast. 10 minutes keeps it under ~1,200 requests/day with room to spare.
    TRAFFIC("Trafic routier", 10 * 60_000L, defaultEnabled = false),
}
