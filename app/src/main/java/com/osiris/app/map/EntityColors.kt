package com.osiris.app.map

/**
 * Single source of truth for the hex colors entities are tinted by — shared between map markers
 * ([LayersController], parsed via `android.graphics.Color`) and info dialog accents
 * ([EntityInfoDialog], parsed via `androidx.compose.ui.graphics.Color`), so a satellite's dialog
 * border always matches its own dot on the map. Mirrors a deliberate pattern in the reference
 * web app's `SatelliteCard.tsx`: "A rule in the satellite's own colour, matching its marker and
 * its track" — verified directly in its source, not guessed from a screenshot.
 */
object EntityColors {
    const val FLIGHT_COMMERCIAL = "#00E5FF"
    const val FLIGHT_PRIVATE = "#FFD54A"
    const val FLIGHT_JET = "#E040FB"
    const val FLIGHT_MILITARY = "#FF5252"

    const val SEVERITY_LOW = "#4CD97B"
    const val SEVERITY_MEDIUM = "#FFB020"
    const val SEVERITY_HIGH = "#FF5252"
    const val SEVERITY_WAR = "#B00020"

    const val FIRE = "#FF7A1A"
    const val WEATHER = "#E040FB"

    const val PORT_CONTAINER = "#00E5FF"
    const val PORT_ENERGY = "#FFB020"
    const val PORT_NAVAL = "#FF5252"
    const val SHIP = "#8AE6C8"

    const val SAT_COMMS = "#00E676"
    const val SAT_NAVIGATION = "#448AFF"
    const val SAT_EARTH_OBS = "#90EE90"
    const val SAT_MILITARY = "#FF3D3D"
    const val SAT_SCIENCE = "#FFD700"
    const val SAT_OTHER = "#00E5FF"

    const val NEWS = "#00E5FF"
    const val CCTV = "#00E5FF"

    // Traffic incident magnitude bands — matches TomTom's own 0-4 scale (see /api/traffic).
    const val TRAFFIC_MINOR = "#4CD97B"
    const val TRAFFIC_MODERATE = "#FFB020"
    const val TRAFFIC_MAJOR = "#FF7A1A"
    const val TRAFFIC_CLOSURE = "#FF3D3D"

    // Cyberattack severity bands/colors — matches the reference web app's popup exactly
    // (OsirisMap.tsx: severity >= 8 critical, >= 6 high, else medium).
    const val CYBER_CRITICAL = "#FF1744"
    const val CYBER_HIGH = "#FF6D00"
    const val CYBER_MEDIUM = "#FFD600"

    fun satelliteCategoryHex(category: String?): String = when (category) {
        "comms" -> SAT_COMMS
        "navigation" -> SAT_NAVIGATION
        "earth_obs" -> SAT_EARTH_OBS
        "military" -> SAT_MILITARY
        "science" -> SAT_SCIENCE
        else -> SAT_OTHER
    }

    fun cyberSeverityHex(severity: Double): String = when {
        severity >= 8 -> CYBER_CRITICAL
        severity >= 6 -> CYBER_HIGH
        else -> CYBER_MEDIUM
    }

    fun cyberSeverityLabel(severity: Double): String = when {
        severity >= 8 -> "CRITIQUE"
        severity >= 6 -> "ÉLEVÉE"
        else -> "MOYENNE"
    }

    fun earthquakeSeverityHex(magnitude: Double?): String = when {
        magnitude == null -> SEVERITY_LOW
        magnitude >= 7 -> SEVERITY_HIGH
        magnitude >= 5 -> SEVERITY_MEDIUM
        else -> SEVERITY_LOW
    }

    fun conflictSeverityHex(severity: String?): String = when (severity) {
        "war" -> SEVERITY_WAR
        "high" -> SEVERITY_HIGH
        "elevated" -> SEVERITY_MEDIUM
        else -> SEVERITY_LOW
    }

    fun trafficMagnitudeHex(magnitude: Int?): String = when (magnitude) {
        4 -> TRAFFIC_CLOSURE
        3 -> TRAFFIC_MAJOR
        2 -> TRAFFIC_MODERATE
        else -> TRAFFIC_MINOR
    }
}
