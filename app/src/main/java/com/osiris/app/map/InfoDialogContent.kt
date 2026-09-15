package com.osiris.app.map

import com.osiris.app.data.model.AircraftDetail
import com.osiris.app.data.model.Chokepoint
import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.model.CyberAttack
import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.model.FlightAirport
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.FlightRoute
import com.osiris.app.data.model.Port
import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.SatelliteNextPass
import com.osiris.app.data.model.Ship
import com.osiris.app.data.model.TrafficIncident
import com.osiris.app.data.model.WeatherEvent
import com.osiris.app.data.source.AisStreamSource
import java.time.Instant

data class InfoRow(val label: String, val value: String)

/** A titled group of [InfoRow]s, rendered by [EntityInfoDialog] as one bordered card with its
 * rows laid out two per line — [title] is omitted (no header, still its own card) for the
 * simpler entity types that only ever have one group. */
data class InfoSection(val title: String? = null, val rows: List<InfoRow>)

/** Origin/destination + progress bar, rendered above the sections — flights only (nothing else
 * tappable on the map has a "route" as such). See [FlightMarker.toInfoDialog]. */
data class FlightRouteHeader(
    val originCode: String?,
    val originCity: String?,
    val destCode: String?,
    val destCity: String?,
    val progress: Float?,
    val departureTime: String?,
    val arrivalTime: String?,
)

/** What [EntityInfoDialog] renders — the same shape for every "tap a dot, see its facts" layer
 * (flights, earthquakes, fires, weather, conflicts, maritime, satellites, cyberattacks).
 * News/CCTV/OSINT keep their own richer dialogs (player, image, link-out) since a flat fact list
 * doesn't fit those. [accentHex] borders the dialog in the entity's own color (severity, or
 * category for satellites) instead of the app's default gold — mirrors the reference web app's
 * `SatelliteCard.tsx`, which deliberately borders a satellite's readout "in the satellite's own
 * colour, matching its marker and its track"; null falls back to the theme's primary color. */
data class InfoDialogContent(
    val title: String,
    val subtitle: String? = null,
    val accentHex: String? = null,
    val routeHeader: FlightRouteHeader? = null,
    val sections: List<InfoSection>,
    /** An "open source ↗" link-out, shown as a button at the bottom of the dialog — the
     * reference web app puts one on nearly every popup (USGS, NASA FIRMS, N2YO, FlightAware,
     * MarineTraffic, a GDELT article...). [externalUrlLabel] defaults to a generic label when
     * null; both are ignored when [externalUrl] itself is null. */
    val externalUrl: String? = null,
    val externalUrlLabel: String? = null,
)

/** One anonymous section wrapping a flat row list — what every entity type besides flights uses;
 * they don't have enough distinct facts to justify splitting into named groups. */
private fun oneSection(rows: List<InfoRow>): List<InfoSection> = listOf(InfoSection(rows = rows))

private fun epochMillisToText(millis: Long?): String? = millis?.let {
    runCatching {
        val local = Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
        "%02d/%02d/%04d %02d:%02d".format(local.dayOfMonth, local.monthValue, local.year, local.hour, local.minute)
    }.getOrNull()
}

/** USGS's own fixed vocabulary for the earthquake `type` field — verbatim from their API docs,
 * not guessed: the large majority of events are "earthquake" itself, the rest are almost always
 * one of these few non-tectonic causes USGS still catalogues on the same feed. */
private fun earthquakeTypeLabel(type: String?): String? = when (type) {
    null -> null
    "earthquake" -> "Séisme"
    "quarry blast" -> "Tir de carrière"
    "explosion" -> "Explosion"
    "quarry" -> "Carrière"
    "nuclear explosion" -> "Explosion nucléaire"
    "rock burst" -> "Coup de terrain"
    "sonic boom" -> "Bang supersonique"
    "landslide" -> "Glissement de terrain"
    "acoustic noise" -> "Bruit acoustique"
    "other event" -> "Autre événement"
    else -> type.replaceFirstChar { it.uppercase() }
}

/** NASA FIRMS reports confidence differently per satellite: VIIRS as "l"/"n"/"h" (low/nominal/
 * high), MODIS as a 0-100 percentage — [com.osiris.app.data.source.NasaFirmsSource] passes
 * whichever the CSV column actually contains straight through unmodified. */
private fun fireConfidenceLabel(confidence: String?): String = when (confidence?.lowercase()) {
    "l", "low" -> "Faible"
    "n", "nominal" -> "Nominale"
    "h", "high" -> "Élevée"
    "unknown", null -> "Inconnue"
    else -> confidence.toIntOrNull()?.let { "$it %" } ?: confidence.replaceFirstChar { it.uppercase() }
}

/** The handful of EONET/app category ids actually produced by [com.osiris.app.data.source.WeatherSource]
 * — falls back to the (now already French, see WeatherSource) `type` label for anything else
 * rather than showing a raw camelCase id the user can't do anything with. */
private fun weatherCategoryLabel(category: String?, fallbackType: String?): String = when (category) {
    "severeStorms" -> "Tempête sévère"
    "volcanoes" -> "Volcan"
    "seaIce" -> "Glace de mer"
    "weatherAlerts" -> "Alerte NWS"
    "gdacs" -> "GDACS"
    "drought" -> "Sécheresse"
    "dustHaze" -> "Poussière / brume"
    "manmade" -> "Origine humaine"
    "snow" -> "Neige"
    "tempExtremes" -> "Température extrême"
    "waterColor" -> "Couleur de l'eau"
    else -> fallbackType ?: "Inconnue"
}

/**
 * [route] (scheduled origin/destination/ETA/progress) and [aircraft] (real flown track +
 * airframe identity) are both fetched on demand after the dialog first opens — see
 * [com.osiris.app.map.MapViewModel.selectFlight] — so this starts with neither and the dialog
 * is rebuilt once they land. [aircraft]'s registration/model/operator are preferred when present
 * (adsb.lol's trace + adsbdb identity lookup) since the live feed's own fields are frequently
 * "Unknown"/"N/A" — OpenSky in particular reports no aircraft type at all.
 */
fun FlightMarker.toInfoDialog(route: FlightRoute? = null, aircraft: AircraftDetail? = null): InfoDialogContent {
    val vol = listOfNotNull(
        InfoRow("Statut", if (flight.grounded) "Au sol" else "En vol"),
        (aircraft?.operator?.takeIf { it.isNotBlank() } ?: flight.airlineCode?.takeIf { it.isNotBlank() })
            ?.let { InfoRow("Compagnie", it) },
        flight.squawk?.takeIf { it.isNotBlank() }?.let { InfoRow("Squawk", it) },
        route?.totalDistanceKm?.let { InfoRow("Distance totale", "$it km") },
        distanceRow("Distance parcourue", route) { total, progress -> total * progress },
        distanceRow("Distance restante", route) { total, progress -> total * (1 - progress) },
    )
    val position = listOfNotNull(
        flight.alt?.let { InfoRow("Altitude", "${it.toInt()} m (${(it * 3.28084).toInt()} ft)") },
        flight.speedKnots?.let { InfoRow("Vitesse sol", "${it.toInt()} kt (${(it * 1.852).toInt()} km/h)") },
        flight.verticalRateFpm?.takeIf { kotlin.math.abs(it) >= 50 }?.let { InfoRow("Vitesse verticale", formatVerticalRate(it)) },
        flight.heading?.let { InfoRow("Cap", "${it.toInt()}°") },
    )
    val avion = listOfNotNull(
        flight.aircraftCategory?.takeIf { it == "heli" }?.let { InfoRow("Type", "Hélicoptère") },
        (aircraft?.model?.takeIf { it.isNotBlank() } ?: flight.model?.takeIf { it != "Unknown" })
            ?.let { InfoRow("Modèle", it) },
        aircraft?.typeCode?.takeIf { it.isNotBlank() }?.let { InfoRow("Code type OACI", it) },
        (aircraft?.registration?.takeIf { it.isNotBlank() } ?: flight.registration?.takeIf { it != "N/A" })
            ?.let { InfoRow("Immatriculation", it) },
        flight.icao24?.takeIf { it.isNotBlank() }?.let { InfoRow("ICAO24", it) },
    )

    return InfoDialogContent(
        title = flight.callsign?.trim().takeUnless { it.isNullOrBlank() } ?: "Vol inconnu",
        subtitle = EntityColors.flightCategoryLabel(category.name),
        routeHeader = route?.takeIf { it.found }?.let {
            FlightRouteHeader(
                originCode = it.origin?.let(::airportCode),
                originCity = it.origin?.city,
                destCode = it.destination?.let(::airportCode),
                destCity = it.destination?.city,
                progress = it.progress?.toFloat(),
                departureTime = it.departureTime?.let(::formatFlightTime),
                arrivalTime = it.arrivalTime?.let(::formatFlightTime),
            )
        },
        sections = listOfNotNull(
            InfoSection("Vol", vol).takeIf { vol.isNotEmpty() },
            InfoSection("Position", position).takeIf { position.isNotEmpty() },
            InfoSection("Avion", avion).takeIf { avion.isNotEmpty() },
        ),
        externalUrl = flight.callsign?.trim()?.takeIf { it.isNotBlank() }
            ?.let { "https://www.flightaware.com/live/flight/${java.net.URLEncoder.encode(it, "UTF-8")}" },
        externalUrlLabel = "FlightAware",
    )
}

private inline fun distanceRow(label: String, route: FlightRoute?, compute: (total: Int, progress: Double) -> Double): InfoRow? {
    val total = route?.totalDistanceKm ?: return null
    val progress = route.progress ?: return null
    return InfoRow(label, "${compute(total, progress).toInt()} km")
}

private fun airportCode(airport: FlightAirport): String? =
    airport.iata?.takeIf { it.isNotBlank() } ?: airport.icao?.takeIf { it.isNotBlank() }

private fun formatFlightTime(iso: String): String? = runCatching {
    val local = Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
    "%02d:%02d".format(local.hour, local.minute)
}.getOrNull()

private fun formatVerticalRate(fpm: Double): String {
    val direction = if (fpm > 0) "Montée" else "Descente"
    return "$direction ${kotlin.math.abs(fpm).toInt()} ft/min"
}

fun Earthquake.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = place ?: "Séisme",
    subtitle = magnitude?.let { "M %.1f".format(it) },
    accentHex = EntityColors.earthquakeSeverityHex(magnitude),
    sections = oneSection(
        listOfNotNull(
            depth?.let { InfoRow("Profondeur", "${it.toInt()} km") },
            epochMillisToText(time)?.let { InfoRow("Date", it) },
            earthquakeTypeLabel(type)?.let { InfoRow("Type", it) },
            tsunami?.takeIf { it == 1 }?.let { InfoRow("Alerte tsunami", "oui") },
        )
    ),
    externalUrl = url,
    externalUrlLabel = "Détails USGS",
)

fun FireEvent.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = title ?: "Incendie",
    subtitle = if (type == "volcano") "Volcan" else "Incendie",
    accentHex = EntityColors.FIRE,
    sections = oneSection(
        listOfNotNull(
            InfoRow("Confiance", fireConfidenceLabel(confidence)),
            brightness?.let { InfoRow("Luminosité", it.toInt().toString()) },
            frp?.let { InfoRow("Puissance radiative (FRP)", it.toInt().toString()) },
            date?.let { InfoRow("Date", it) },
        )
    ),
    // No per-fire link from the backend — same map view the reference web app links to,
    // built from this fire's own coordinates instead of a field it doesn't have.
    externalUrl = "https://firms.modaps.eosdis.nasa.gov/map/#d:24hrs;l:noaa20-viirs,viirs,modis_a,modis_t;@$lng,$lat,10z",
    externalUrlLabel = "NASA FIRMS",
)

fun WeatherEvent.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = title ?: "Événement météo",
    subtitle = when (severity) {
        "high" -> "Sévère"
        "medium" -> "Modérée"
        else -> "Faible"
    },
    accentHex = EntityColors.WEATHER,
    sections = oneSection(
        listOfNotNull(
            InfoRow("Catégorie", weatherCategoryLabel(category, type)),
            type?.let { InfoRow("Type", it) },
            date?.let { InfoRow("Date", it) },
            expires?.let { InfoRow("Expire", it) },
        )
    ),
    externalUrl = source,
    externalUrlLabel = "Source",
)

fun ConflictZone.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = label,
    subtitle = EntityColors.conflictSeverityLabel(severity),
    accentHex = EntityColors.conflictSeverityHex(severity),
    sections = oneSection(
        listOfNotNull(
            region?.let { InfoRow("Région", it) },
            description?.let { InfoRow("Description", it) },
            eventCount.takeIf { it > 0 }?.let { InfoRow("Événements récents", it.toString()) },
            lastUpdated?.let { InfoRow("Mis à jour", it) },
        )
    ),
    externalUrl = sourceUrl,
    externalUrlLabel = "Source",
)

fun Port.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = name,
    subtitle = EntityColors.portTypeLabel(type),
    accentHex = when (type) {
        "energy" -> EntityColors.PORT_ENERGY
        "naval" -> EntityColors.PORT_NAVAL
        else -> EntityColors.PORT_CONTAINER
    },
    sections = oneSection(
        listOfNotNull(
            country?.let { InfoRow("Pays", it) },
            volume?.let { InfoRow("Volume", it) },
            rank?.let { InfoRow("Rang mondial", "#$it") },
            fleet?.let { InfoRow("Flotte", it) },
            congestion?.let { InfoRow("Congestion", EntityColors.portCongestionLabel(it)) },
            dwellTime?.let { InfoRow("Temps d'attente estimé", it) },
        )
    ),
)

fun Chokepoint.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = name,
    subtitle = EntityColors.chokepointRiskLabel(risk),
    sections = oneSection(listOfNotNull(traffic?.let { InfoRow("Trafic", it) })),
)

/** Most of these fields ([Ship.navStatus]/[Ship.callSign]/[Ship.imo]/dimensions/[Ship.etaText])
 * come straight from the AIS stream — [com.osiris.app.data.source.AisStreamSource] already
 * received them, they just weren't captured/shown before. [navStatusLabel] in particular answers
 * "why isn't this moving" right in the dialog instead of leaving it a mystery: 1/5/6 (anchored/
 * moored/aground) aren't a tracking bug, they're the ship's actual real-world state. */
fun Ship.toInfoDialog(): InfoDialogContent {
    val identity = listOfNotNull(
        mmsi?.let { InfoRow("MMSI", it.toString()) },
        AisStreamSource.mmsiFlagCountry(mmsi)?.let { InfoRow("Pavillon", it) },
        callSign?.let { InfoRow("Indicatif", it) },
        imo?.let { InfoRow("Numéro IMO", it.toString()) },
        if (lengthM != null && widthM != null) InfoRow("Dimensions", "${lengthM.toInt()} × ${widthM.toInt()} m") else null,
        draughtM?.let { InfoRow("Tirant d'eau", "%.1f m".format(it)) },
    )
    val position = listOfNotNull(
        AisStreamSource.navStatusLabel(navStatus)?.let { InfoRow("Statut", it) },
        destination?.takeIf { it.isNotBlank() }?.let { InfoRow("Destination", it) },
        etaText?.let { InfoRow("ETA", it) },
        speed?.let { InfoRow("Vitesse", "${it.toInt()} kt") },
        heading?.let { InfoRow("Cap", "${it.toInt()}°") },
    )
    return InfoDialogContent(
        title = name?.takeIf { it.isNotBlank() } ?: "Navire",
        subtitle = EntityColors.shipTypeLabel(type),
        accentHex = EntityColors.SHIP,
        sections = listOfNotNull(
            InfoSection("Navire", identity).takeIf { identity.isNotEmpty() },
            InfoSection("Position", position).takeIf { position.isNotEmpty() },
        ),
        externalUrl = mmsi?.let { "https://www.marinetraffic.com/en/ais/details/ships/mmsi:$it" },
        externalUrlLabel = "MarineTraffic",
    )
}

/** [periodMinutes] and [nextPass] are both fetched on demand after the dialog first opens (see
 * [com.osiris.app.map.MapViewModel.selectSatellite]) — the main poll only ever carries a bare
 * lat/lng/alt, unlike every other tappable layer, so both start null/unattempted and the dialog
 * is rebuilt once each lands. Speed is derived from the period the same way the reference web
 * app's `SatelliteCard.tsx` does — circular-orbit speed implied by the period, "the point of it
 * is scale, not precision". [nextPassAttempted] distinguishes "still fetching" (show "…") from
 * "fetched, nothing found" (no device location, or the satellite genuinely never rises for this
 * observer — a GEO parked elsewhere, say) since both leave [nextPass] null. */
fun Satellite.toInfoDialog(
    periodMinutes: Double? = null,
    nextPass: SatelliteNextPass? = null,
    nextPassAttempted: Boolean = false,
): InfoDialogContent = InfoDialogContent(
    title = name,
    subtitle = EntityColors.satelliteMissionLabel(mission),
    accentHex = EntityColors.satelliteCategoryHex(category),
    sections = listOfNotNull(
        InfoSection(
            rows = listOfNotNull(
                noradId?.let { InfoRow("NORAD ID", it) },
                InfoRow("Catégorie", EntityColors.satelliteCategoryLabel(category)),
                launchYear?.let { InfoRow("Lancement", it.toString()) },
                alt?.let { InfoRow("Altitude", "${it.toInt()} km") },
                alt?.let { InfoRow("Orbite", orbitClass(it)) },
                periodMinutes?.let { InfoRow("Période orbitale", formatOrbitalPeriod(it)) } ?: InfoRow("Période orbitale", "…"),
                if (alt != null && periodMinutes != null) InfoRow("Vitesse", "${"%.2f".format(orbitalSpeedKmS(alt, periodMinutes))} km/s") else null,
                inclinationDeg?.let { InfoRow("Inclinaison", "%.1f°".format(it)) },
                eccentricity?.let { InfoRow("Excentricité", "%.4f".format(it)) },
                if (apogeeAltKm != null && perigeeAltKm != null) {
                    InfoRow("Apogée / Périgée", "${apogeeAltKm.toInt()} / ${perigeeAltKm.toInt()} km")
                } else null,
            ),
        ),
        InfoSection(
            title = "Prochain passage au-dessus de vous",
            rows = when {
                nextPass != null -> listOf(
                    InfoRow("Lever", formatPassTime(nextPass.startTimeMs)),
                    InfoRow("Durée", "${((nextPass.endTimeMs - nextPass.startTimeMs) / 60_000.0).toInt()} min"),
                    InfoRow("Élévation max", "${nextPass.maxElevationDeg.toInt()}°"),
                    InfoRow("Azimut lever → coucher", "${nextPass.aosAzimuthDeg}° → ${nextPass.losAzimuthDeg}°"),
                )
                nextPassAttempted -> listOf(InfoRow("Passage", "Aucun trouvé (position indisponible ou jamais visible)"))
                else -> listOf(InfoRow("Passage", "…"))
            },
        ),
    ),
    externalUrl = noradId?.let { "https://www.n2yo.com/satellite/?s=$it" },
    externalUrlLabel = "Suivre sur N2YO",
)

private fun formatPassTime(epochMs: Long): String = runCatching {
    val local = Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
    "%02d/%02d %02d:%02d".format(local.dayOfMonth, local.monthValue, local.hour, local.minute)
}.getOrDefault("—")

private fun orbitClass(altKm: Double): String = when {
    altKm < 2_000.0 -> "LEO — orbite basse"
    altKm < 35_000.0 -> "MEO — orbite moyenne"
    altKm < 36_500.0 -> "GEO — géostationnaire"
    else -> "HEO — orbite haute"
}

private fun formatOrbitalPeriod(minutes: Double): String {
    val hours = (minutes / 60).toInt()
    val remainingMinutes = (minutes % 60).toInt()
    return if (hours > 0) "${hours} h ${remainingMinutes} min" else "${remainingMinutes} min"
}

private const val EARTH_RADIUS_KM = 6_371.0

private fun orbitalSpeedKmS(altKm: Double, periodMinutes: Double): Double =
    (2 * Math.PI * (EARTH_RADIUS_KM + altKm)) / (periodMinutes * 60)

/** No enrichment fetch needed (unlike flights/satellites) — everything shown is already in the
 * poll. Accent/severity thresholds match the reference web app's cyberattack popup exactly. */
fun CyberAttack.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = (action?.takeIf { it.isNotBlank() } ?: "Cyberattaque").uppercase(),
    subtitle = malware?.takeIf { it.isNotBlank() } ?: "Charge utile inconnue",
    accentHex = EntityColors.cyberSeverityHex(severity.toDouble()),
    sections = oneSection(
        listOfNotNull(
            InfoRow("Sévérité", "$severity/10 (${EntityColors.cyberSeverityLabel(severity.toDouble())})"),
            targetIp?.takeIf { it.isNotBlank() }?.let { InfoRow("Cible", it) },
            targetCountry?.takeIf { it.isNotBlank() }?.let { InfoRow("Pays cible", it) },
            InfoRow("Origine", "%.1f°, %.1f°".format(srcLat, srcLng)),
        )
    ),
)

/** No enrichment fetch needed — the poll already carries everything TomTom gave the backend.
 * Accent follows the same 0-4 magnitude scale TomTom itself uses (see
 * [EntityColors.trafficMagnitudeHex]); external link is a plain Google Maps pin since TomTom
 * has no public per-incident page to link out to the way N2YO/FlightAware/MarineTraffic do. */
fun TrafficIncident.toInfoDialog(): InfoDialogContent = InfoDialogContent(
    title = category.replaceFirstChar { it.uppercase() },
    subtitle = road ?: description,
    accentHex = EntityColors.trafficMagnitudeHex(magnitude),
    sections = oneSection(
        listOfNotNull(
            description?.takeIf { it.isNotBlank() && it != category }?.let { InfoRow("Détail", it) },
            from?.takeIf { it.isNotBlank() }?.let { InfoRow("De", it) },
            to?.takeIf { it.isNotBlank() }?.let { InfoRow("À", it) },
            lengthM?.let { InfoRow("Longueur", "${it.toInt()} m") },
            magnitude?.let { InfoRow("Ampleur", trafficMagnitudeLabel(it)) },
        )
    ),
    externalUrl = "https://www.google.com/maps?q=$lat,$lng",
    externalUrlLabel = "Voir sur la carte",
)

private fun trafficMagnitudeLabel(magnitude: Int): String = when (magnitude) {
    4 -> "Fermeture"
    3 -> "Majeure"
    2 -> "Modérée"
    1 -> "Mineure"
    else -> "Inconnue"
}
