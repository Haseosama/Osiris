package com.osiris.app.map

import com.osiris.app.data.model.Chokepoint
import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.Port
import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.Ship
import com.osiris.app.data.model.WeatherEvent
import java.time.Instant

data class InfoRow(val label: String, val value: String)

/** What [EntityInfoDialog] renders — the same shape for every "tap a dot, see its facts" layer
 * (flights, earthquakes, fires, weather, conflicts, maritime, satellites). News/CCTV/OSINT keep
 * their own richer dialogs (player, image, link-out) since a flat fact list doesn't fit those. */
data class InfoDialogContent(val title: String, val subtitle: String? = null, val rows: List<InfoRow>)

private fun epochMillisToText(millis: Long?): String? =
    millis?.let { runCatching { Instant.ofEpochMilli(it).toString() }.getOrNull() }

fun FlightMarker.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = flight.callsign?.trim().takeUnless { it.isNullOrBlank() } ?: "Vol inconnu",
    subtitle = category.name,
    rows = listOfNotNull(
        flight.model?.takeIf { it != "Unknown" }?.let { InfoRow("Modèle", it) },
        flight.registration?.takeIf { it != "N/A" }?.let { InfoRow("Immatriculation", it) },
        flight.icao24?.takeIf { it.isNotBlank() }?.let { InfoRow("ICAO24", it) },
        flight.alt?.let { InfoRow("Altitude", "${it.toInt()} m") },
        flight.speedKnots?.let { InfoRow("Vitesse", "${it.toInt()} kt") },
        flight.heading?.let { InfoRow("Cap", "${it.toInt()}°") },
        flight.squawk?.takeIf { it.isNotBlank() }?.let { InfoRow("Squawk", it) },
    ),
)

fun Earthquake.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = place ?: "Séisme",
    subtitle = magnitude?.let { "M %.1f".format(it) },
    rows = listOfNotNull(
        depth?.let { InfoRow("Profondeur", "${it.toInt()} km") },
        epochMillisToText(time)?.let { InfoRow("Date", it) },
        type?.let { InfoRow("Type", it) },
        tsunami?.takeIf { it == 1 }?.let { InfoRow("Alerte tsunami", "oui") },
    ),
)

fun FireEvent.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = title ?: "Incendie",
    subtitle = type,
    rows = listOfNotNull(
        confidence?.let { InfoRow("Confiance", it) },
        brightness?.let { InfoRow("Luminosité", it.toInt().toString()) },
        frp?.let { InfoRow("Puissance radiative (FRP)", it.toInt().toString()) },
        date?.let { InfoRow("Date", it) },
    ),
)

fun WeatherEvent.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = title ?: "Événement météo",
    subtitle = severity,
    rows = listOfNotNull(
        category?.let { InfoRow("Catégorie", it) },
        type?.let { InfoRow("Type", it) },
        date?.let { InfoRow("Date", it) },
        expires?.let { InfoRow("Expire", it) },
    ),
)

fun ConflictZone.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = label,
    subtitle = severity,
    rows = listOfNotNull(
        region?.let { InfoRow("Région", it) },
        description?.let { InfoRow("Description", it) },
        eventCount.takeIf { it > 0 }?.let { InfoRow("Événements récents", it.toString()) },
        lastUpdated?.let { InfoRow("Mis à jour", it) },
    ),
)

fun Port.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = name,
    subtitle = type,
    rows = listOfNotNull(
        country?.let { InfoRow("Pays", it) },
        volume?.let { InfoRow("Volume", it) },
        congestion?.let { InfoRow("Congestion", it) },
    ),
)

fun Chokepoint.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = name,
    subtitle = risk,
    rows = listOfNotNull(
        traffic?.let { InfoRow("Trafic", it) },
    ),
)

fun Ship.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = name?.takeIf { it.isNotBlank() } ?: "Navire",
    subtitle = type,
    rows = listOfNotNull(
        mmsi?.let { InfoRow("MMSI", it.toString()) },
        destination?.takeIf { it.isNotBlank() }?.let { InfoRow("Destination", it) },
        speed?.let { InfoRow("Vitesse", "${it.toInt()} kt") },
        heading?.let { InfoRow("Cap", "${it.toInt()}°") },
    ),
)

fun Satellite.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = name,
    subtitle = mission,
    rows = listOfNotNull(
        noradId?.let { InfoRow("NORAD ID", it) },
        category?.let { InfoRow("Catégorie", it) },
        alt?.let { InfoRow("Altitude", "${it.toInt()} km") },
    ),
)
