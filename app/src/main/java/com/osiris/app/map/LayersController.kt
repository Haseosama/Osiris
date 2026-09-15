package com.osiris.app.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.osiris.app.R
import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.model.CyberAttack
import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.LiveNewsFeed
import com.osiris.app.data.model.MaritimeResponse
import com.osiris.app.data.model.OsintPost
import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.TrafficIncident
import com.osiris.app.data.model.WeatherEvent
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private val FLIGHT_COMMERCIAL = EntityColors.FLIGHT_COMMERCIAL.toColorInt()
private val FLIGHT_PRIVATE = EntityColors.FLIGHT_PRIVATE.toColorInt()
private val FLIGHT_JET = EntityColors.FLIGHT_JET.toColorInt()
private val FLIGHT_MILITARY = EntityColors.FLIGHT_MILITARY.toColorInt()

private val SEVERITY_LOW = EntityColors.SEVERITY_LOW.toColorInt()
private val SEVERITY_MEDIUM = EntityColors.SEVERITY_MEDIUM.toColorInt()
private val SEVERITY_HIGH = EntityColors.SEVERITY_HIGH.toColorInt()
private val SEVERITY_WAR = EntityColors.SEVERITY_WAR.toColorInt()

private val FIRE_COLOR = EntityColors.FIRE.toColorInt()

private val PORT_CONTAINER = EntityColors.PORT_CONTAINER.toColorInt()
private val PORT_ENERGY = EntityColors.PORT_ENERGY.toColorInt()
private val PORT_NAVAL = EntityColors.PORT_NAVAL.toColorInt()
private val SHIP_COLOR = EntityColors.SHIP.toColorInt()

private val SAT_COMMS = EntityColors.SAT_COMMS.toColorInt()
private val SAT_NAVIGATION = EntityColors.SAT_NAVIGATION.toColorInt()
private val SAT_EARTH_OBS = EntityColors.SAT_EARTH_OBS.toColorInt()
private val SAT_MILITARY = EntityColors.SAT_MILITARY.toColorInt()
private val SAT_SCIENCE = EntityColors.SAT_SCIENCE.toColorInt()
private val SAT_OTHER = EntityColors.SAT_OTHER.toColorInt()

private val NEWS_COLOR = EntityColors.NEWS.toColorInt()

private val CCTV_COLOR = EntityColors.CCTV.toColorInt()

private val TRAFFIC_MINOR = EntityColors.TRAFFIC_MINOR.toColorInt()
private val TRAFFIC_MODERATE = EntityColors.TRAFFIC_MODERATE.toColorInt()
private val TRAFFIC_MAJOR = EntityColors.TRAFFIC_MAJOR.toColorInt()
private val TRAFFIC_CLOSURE = EntityColors.TRAFFIC_CLOSURE.toColorInt()

/**
 * Adds/updates one GeoJSON source + circle layer per [MapLayer] on the given MapLibre [style].
 * Data is pushed in via `set*` whenever a repository poll completes; visibility is toggled
 * independently so a layer can be hidden without losing its cached data.
 *
 * [addTerrain] wires up [ensureTerrain] once at construction — a style switch rebuilds this whole
 * controller against a fresh [Style] (see MapScreen's LaunchedEffect(mapStyleMode)), so there's no
 * separate "did we already add it" state to track across switches the way [registeredImages] does
 * within one style's lifetime.
 */
class LayersController(private val style: Style, private val context: Context, addTerrain: Boolean = false) {

    init {
        if (addTerrain) ensureTerrain()
    }

    // Flights/ships/satellites rebuild their whole feature list at the dead-reckoning/live-
    // propagation tick rate (100-250ms — see MapViewModel) rather than only on a poll, so unlike
    // every other set* below they're worth keeping off the UI thread: mapIndexed{} over a few
    // hundred entities several times a second is exactly the kind of steady drip of main-thread
    // work that reads as janky scrolling/panning even though no single call is slow on its own.
    // Only the pure-data construction moves to Dispatchers.Default — style.addImage/addSource/
    // getSource/setGeoJson stay on the caller's thread throughout, since MapLibre's Style/Source
    // classes are @UiThread.
    suspend fun setFlights(markers: List<FlightMarker>) {
        ensureImage("flight-commercial", R.drawable.ic_plane, FLIGHT_COMMERCIAL)
        ensureImage("flight-private", R.drawable.ic_plane, FLIGHT_PRIVATE)
        ensureImage("flight-jet", R.drawable.ic_plane, FLIGHT_JET)
        ensureImage("flight-military", R.drawable.ic_plane, FLIGHT_MILITARY)

        val features = withContext(Dispatchers.Default) {
            markers.mapIndexed { index, marker ->
                feature(marker.flight.lng, marker.flight.lat) {
                    addNumberProperty("idx", index)
                    addStringProperty("category", marker.category.name)
                    marker.flight.callsign?.let { addStringProperty("callsign", it) }
                    marker.flight.heading?.let { addNumberProperty("heading", it) }
                }
            }
        }
        updateSource("flights-source", features)
        ensureSymbolLayer(
            layerId = "flights-layer",
            sourceId = "flights-source",
            iconImage = PropertyFactory.iconImage(
                Expression.match(
                    Expression.get("category"),
                    Expression.literal("flight-commercial"),
                    Expression.stop("COMMERCIAL", Expression.literal("flight-commercial")),
                    Expression.stop("PRIVATE", Expression.literal("flight-private")),
                    Expression.stop("JET", Expression.literal("flight-jet")),
                    Expression.stop("MILITARY", Expression.literal("flight-military")),
                )
            ),
            iconSize = PropertyFactory.iconSize(0.7f),
            iconRotate = PropertyFactory.iconRotate(Expression.get("heading")),
        )
    }

    /** The selected flight's trajectory — either its actual flown path (adsb.lol trace) or a
     * synthetic great-circle route, whichever MapViewModel.FlightEnrichment.trackForMap picked.
     * Null/empty clears the line (dialog closed, or nothing to draw for this flight). */
    fun setFlightTrack(track: List<List<Double>>?) {
        val features = if (track.isNullOrEmpty()) {
            emptyList()
        } else {
            val line = LineString.fromLngLats(track.map { (lng, lat) -> Point.fromLngLat(lng, lat) })
            listOf(Feature.fromGeometry(line))
        }
        updateSource("flight-track-source", features)
        if (style.getLayer("flight-track-layer") == null) {
            val layer = LineLayer("flight-track-layer", "flight-track-source").withProperties(
                PropertyFactory.lineColor(FLIGHT_COMMERCIAL),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.85f),
                PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
            )
            style.addLayer(layer)
        }
    }

    fun setEarthquakes(earthquakes: List<Earthquake>) {
        val features = earthquakes.mapIndexed { index, quake ->
            feature(quake.lng, quake.lat) {
                addNumberProperty("idx", index)
                quake.magnitude?.let { addNumberProperty("magnitude", it) }
                quake.place?.let { addStringProperty("place", it) }
            }
        }
        updateSource("earthquakes-source", features)
        ensureCircleLayer(
            layerId = "earthquakes-layer",
            sourceId = "earthquakes-source",
            color = PropertyFactory.circleColor("#FFB020".toColorInt()),
            radius = PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("magnitude"),
                    Expression.stop(2f, Expression.literal(4f)),
                    Expression.stop(8f, Expression.literal(22f)),
                )
            ),
        )
    }

    fun setFires(fires: List<FireEvent>) {
        ensureImage("fire-icon", R.drawable.ic_flame, FIRE_COLOR)

        val features = fires.mapIndexed { index, fire ->
            feature(fire.lng, fire.lat) {
                addNumberProperty("idx", index)
                fire.title?.let { addStringProperty("title", it) }
            }
        }
        updateSource("fires-source", features)
        ensureSymbolLayer(
            layerId = "fires-layer",
            sourceId = "fires-source",
            iconImage = PropertyFactory.iconImage("fire-icon"),
            iconSize = PropertyFactory.iconSize(0.45f),
        )
    }

    fun setWeatherEvents(events: List<WeatherEvent>) {
        val features = events.mapIndexed { index, event ->
            feature(event.lng, event.lat) {
                addNumberProperty("idx", index)
                event.title?.let { addStringProperty("title", it) }
                addStringProperty("severity", event.severity ?: "low")
            }
        }
        updateSource("weather-source", features)
        ensureCircleLayer(
            layerId = "weather-layer",
            sourceId = "weather-source",
            color = PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("severity"),
                    Expression.color(SEVERITY_LOW),
                    Expression.stop("low", Expression.color(SEVERITY_LOW)),
                    Expression.stop("medium", Expression.color(SEVERITY_MEDIUM)),
                    Expression.stop("high", Expression.color(SEVERITY_HIGH)),
                )
            ),
            radius = PropertyFactory.circleRadius(6f),
        )
    }

    fun setConflictZones(zones: List<ConflictZone>) {
        val features = zones.mapIndexed { index, zone ->
            feature(zone.lng, zone.lat) {
                addNumberProperty("idx", index)
                addStringProperty("label", zone.label)
                addStringProperty("severity", zone.severity)
            }
        }
        updateSource("conflicts-source", features)
        ensureCircleLayer(
            layerId = "conflicts-layer",
            sourceId = "conflicts-source",
            color = PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("severity"),
                    Expression.color(SEVERITY_MEDIUM),
                    Expression.stop("moderate", Expression.color(SEVERITY_LOW)),
                    Expression.stop("elevated", Expression.color(SEVERITY_MEDIUM)),
                    Expression.stop("high", Expression.color(SEVERITY_HIGH)),
                    Expression.stop("war", Expression.color(SEVERITY_WAR)),
                )
            ),
            radius = PropertyFactory.circleRadius(10f),
        )
    }

    // Called on every maritime dead-reckoning tick (100ms — see MapViewModel), not just the 20s
    // poll, since ships (unlike ports/chokepoints) move — see setFlights' own doc for why the
    // feature-list construction specifically moves off the UI thread here.
    suspend fun setMaritime(maritime: MaritimeResponse) {
        ensureImage("port-container", R.drawable.ic_anchor, PORT_CONTAINER)
        ensureImage("port-energy", R.drawable.ic_anchor, PORT_ENERGY)
        ensureImage("port-naval", R.drawable.ic_anchor, PORT_NAVAL)
        ensureImage("ship-icon", R.drawable.ic_boat, SHIP_COLOR)

        val (portFeatures, chokepointFeatures, shipFeatures) = withContext(Dispatchers.Default) {
            Triple(
                maritime.ports.mapIndexed { index, port ->
                    feature(port.lng, port.lat) {
                        addNumberProperty("idx", index)
                        addStringProperty("name", port.name)
                        addStringProperty("type", port.type)
                    }
                },
                maritime.chokepoints.mapIndexed { index, choke ->
                    feature(choke.lng, choke.lat) {
                        addNumberProperty("idx", index)
                        addStringProperty("name", choke.name)
                        addStringProperty("risk", choke.risk)
                    }
                },
                maritime.ships.mapIndexed { index, ship ->
                    feature(ship.lng, ship.lat) {
                        addNumberProperty("idx", index)
                        ship.name?.let { addStringProperty("name", it) }
                        ship.type?.let { addStringProperty("type", it) }
                        ship.heading?.let { addNumberProperty("heading", it) }
                    }
                },
            )
        }

        updateSource("ports-source", portFeatures)
        ensureSymbolLayer(
            layerId = "ports-layer",
            sourceId = "ports-source",
            iconImage = PropertyFactory.iconImage(
                Expression.match(
                    Expression.get("type"),
                    Expression.literal("port-container"),
                    Expression.stop("container", Expression.literal("port-container")),
                    Expression.stop("energy", Expression.literal("port-energy")),
                    Expression.stop("naval", Expression.literal("port-naval")),
                )
            ),
            iconSize = PropertyFactory.iconSize(0.55f),
        )

        updateSource("chokepoints-source", chokepointFeatures)
        ensureCircleLayer(
            layerId = "chokepoints-layer",
            sourceId = "chokepoints-source",
            color = PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("risk"),
                    Expression.color(SEVERITY_MEDIUM),
                    Expression.stop("LOW", Expression.color(SEVERITY_LOW)),
                    Expression.stop("MODERATE", Expression.color(SEVERITY_MEDIUM)),
                    Expression.stop("ELEVATED", Expression.color(SEVERITY_MEDIUM)),
                    Expression.stop("HIGH", Expression.color(SEVERITY_HIGH)),
                    Expression.stop("CRITICAL", Expression.color(SEVERITY_WAR)),
                )
            ),
            radius = PropertyFactory.circleRadius(9f),
        )

        updateSource("ships-source", shipFeatures)
        ensureSymbolLayer(
            layerId = "ships-layer",
            sourceId = "ships-source",
            iconImage = PropertyFactory.iconImage("ship-icon"),
            iconSize = PropertyFactory.iconSize(0.5f),
            iconRotate = PropertyFactory.iconRotate(Expression.get("heading")),
        )
    }

    // Called on every satellite re-propagation tick (250ms — see startSatellitesAnimation); same
    // reasoning as setFlights for why the feature-list construction moves off the UI thread.
    suspend fun setSatellites(satellites: List<Satellite>) {
        ensureImage("sat-comms", R.drawable.ic_satellite, SAT_COMMS)
        ensureImage("sat-navigation", R.drawable.ic_satellite, SAT_NAVIGATION)
        ensureImage("sat-earth_obs", R.drawable.ic_satellite, SAT_EARTH_OBS)
        ensureImage("sat-military", R.drawable.ic_satellite, SAT_MILITARY)
        ensureImage("sat-science", R.drawable.ic_satellite, SAT_SCIENCE)
        ensureImage("sat-other", R.drawable.ic_satellite, SAT_OTHER)

        val features = withContext(Dispatchers.Default) {
            satellites.mapIndexed { index, sat ->
                feature(sat.lng, sat.lat) {
                    addNumberProperty("idx", index)
                    addStringProperty("name", sat.name)
                    addStringProperty("category", sat.category ?: "other")
                    sat.mission?.let { addStringProperty("mission", it) }
                }
            }
        }
        updateSource("satellites-source", features)
        ensureSymbolLayer(
            layerId = "satellites-layer",
            sourceId = "satellites-source",
            iconImage = PropertyFactory.iconImage(
                Expression.match(
                    Expression.get("category"),
                    Expression.literal("sat-other"),
                    Expression.stop("comms", Expression.literal("sat-comms")),
                    Expression.stop("navigation", Expression.literal("sat-navigation")),
                    Expression.stop("earth_obs", Expression.literal("sat-earth_obs")),
                    Expression.stop("military", Expression.literal("sat-military")),
                    Expression.stop("science", Expression.literal("sat-science")),
                    Expression.stop("other", Expression.literal("sat-other")),
                )
            ),
            iconSize = PropertyFactory.iconSize(0.5f),
        )
    }

    fun setNewsFeeds(feeds: List<LiveNewsFeed>) {
        val features = feeds.map { feed ->
            feature(feed.lng, feed.lat) {
                addStringProperty("id", feed.id)
                addStringProperty("name", feed.name)
                addStringProperty("url", feed.url)
                addBooleanProperty("embedAllowed", feed.embedAllowed)
            }
        }
        updateSource("news-source", features)
        ensureCircleLayer(
            layerId = "news-layer",
            sourceId = "news-source",
            color = PropertyFactory.circleColor(NEWS_COLOR),
            radius = PropertyFactory.circleRadius(7f),
        )
    }

    fun setCyberAttacks(attacks: List<CyberAttack>) {
        val features = attacks.mapIndexed { index, attack ->
            // A curved arc, not a straight line — see ArcMath — echoing the flying arcs the
            // web app animates for these. The MapViewModel-driven pulse layer below travels
            // along this exact same curve.
            val curve = ArcMath.curve(attack.srcLng, attack.srcLat, attack.dstLng, attack.dstLat)
            val line = LineString.fromLngLats(curve.map { (lng, lat) -> Point.fromLngLat(lng, lat) })
            Feature.fromGeometry(line).apply {
                addNumberProperty("idx", index)
                addNumberProperty("severity", attack.severity)
                attack.malware?.let { addStringProperty("malware", it) }
            }
        }
        updateSource("cyber-attacks-source", features)
        if (style.getLayer("cyber-attacks-layer") == null) {
            val layer = LineLayer("cyber-attacks-layer", "cyber-attacks-source").withProperties(
                PropertyFactory.lineColor(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.get("severity"),
                        Expression.stop(5f, Expression.color(SEVERITY_MEDIUM)),
                        Expression.stop(10f, Expression.color(SEVERITY_WAR)),
                    )
                ),
                PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineOpacity(0.5f),
            )
            style.addLayer(layer)
        }
    }

    /** A small moving dot per attack, position recomputed by MapViewModel every ~80ms along
     * the same [ArcMath] curve [setCyberAttacks] draws — the "flying" part of the flying arc. */
    fun setCyberAttackPulses(pulses: List<CyberAttackPulse>) {
        val features = pulses.map { pulse ->
            feature(pulse.lng, pulse.lat) {
                addNumberProperty("severity", pulse.severity)
            }
        }
        updateSource("cyber-attacks-pulse-source", features)
        ensureCircleLayer(
            layerId = "cyber-attacks-pulse-layer",
            sourceId = "cyber-attacks-pulse-source",
            color = PropertyFactory.circleColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("severity"),
                    Expression.stop(5f, Expression.color(SEVERITY_MEDIUM)),
                    Expression.stop(10f, Expression.color(SEVERITY_WAR)),
                )
            ),
            radius = PropertyFactory.circleRadius(3.5f),
        )
    }

    /** ~17k cameras in one source — clustered via [ensureClusteredSource]/[ensureClusterCircleLayers]
     * so nearby cameras collapse into a count bubble until zoomed in enough to split apart. */
    fun setCctv(cameras: List<CctvCamera>) {
        ensureImage("cctv-camera", R.drawable.ic_camera, CCTV_COLOR)

        val features = cameras.map { cam ->
            feature(cam.lng, cam.lat) {
                addStringProperty("id", cam.id)
                addStringProperty("name", cam.name ?: "Caméra")
                cam.feedUrl?.let { addStringProperty("feedUrl", it) }
                cam.streamUrl?.let { addStringProperty("streamUrl", it) }
            }
        }
        ensureClusteredSource("cctv-source", features)
        ensureClusterCircleLayers("cctv-source", "cctv-clusters", "cctv-cluster-count", CCTV_COLOR)
        ensureSymbolLayer(
            layerId = "cctv-unclustered",
            sourceId = "cctv-source",
            iconImage = PropertyFactory.iconImage("cctv-camera"),
            // Bigger when zoomed out (a lone unclustered dot is easy to lose in a huge visible
            // area) tapering down to a normal size once zoomed in close to it.
            iconSize = PropertyFactory.iconSize(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(3f, Expression.literal(1.4f)),
                    Expression.stop(8f, Expression.literal(1.1f)),
                    Expression.stop(14f, Expression.literal(0.85f)),
                )
            ),
            filter = Expression.not(Expression.has("point_count")),
        )
    }

    fun setOsintPosts(posts: List<OsintPost>) {
        val features = posts.mapNotNull { post ->
            val lat = post.lat ?: return@mapNotNull null
            val lng = post.lng ?: return@mapNotNull null
            feature(lng, lat) {
                addStringProperty("id", post.id)
                addStringProperty("title", post.title)
                addNumberProperty("riskScore", post.riskScore)
            }
        }
        updateSource("osint-source", features)
        ensureCircleLayer(
            layerId = "osint-layer",
            sourceId = "osint-source",
            color = PropertyFactory.circleColor(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.get("riskScore"),
                    Expression.stop(1, Expression.color(SEVERITY_LOW)),
                    Expression.stop(6, Expression.color(SEVERITY_MEDIUM)),
                    Expression.stop(10, Expression.color(SEVERITY_WAR)),
                )
            ),
            radius = PropertyFactory.circleRadius(6f),
        )
    }

    /** TomTom's own 0-4 magnitude scale, straight from the backend — see
     * [EntityColors.trafficMagnitudeHex] for the same bands used on the dialog accent. Draws two
     * layers from the same data: the affected road segment itself (a colored line — the real
     * geometry TomTom reports, not a synthetic shape) plus a small circle at its midpoint, which
     * stays the actual tap target (`idx`-indexed, matching [MapScreen]'s `handleInfoTap` — the
     * line features don't carry that index, so tapping the line itself does nothing, only the
     * dot). An incident with no usable geometry (fewer than 2 coordinate pairs) still gets its
     * dot, just no line. */
    fun setTrafficIncidents(incidents: List<TrafficIncident>) {
        val pointFeatures = incidents.mapIndexed { index, incident ->
            feature(incident.lng, incident.lat) {
                addNumberProperty("idx", index)
                addNumberProperty("magnitude", incident.magnitude ?: 0)
            }
        }
        updateSource("traffic-source", pointFeatures)
        ensureCircleLayer(
            layerId = "traffic-layer",
            sourceId = "traffic-source",
            color = PropertyFactory.circleColor(trafficMagnitudeColorExpression()),
            radius = PropertyFactory.circleRadius(6f),
        )

        val lineFeatures = incidents.mapNotNull { incident ->
            val points = incident.geometry
                ?.mapNotNull { pair -> pair.takeIf { it.size >= 2 }?.let { Point.fromLngLat(it[0], it[1]) } }
                ?: emptyList()
            if (points.size < 2) return@mapNotNull null
            Feature.fromGeometry(LineString.fromLngLats(points)).apply {
                addNumberProperty("magnitude", incident.magnitude ?: 0)
            }
        }
        updateSource("traffic-lines-source", lineFeatures)
        if (style.getLayer("traffic-lines-layer") == null) {
            val layer = LineLayer("traffic-lines-layer", "traffic-lines-source").withProperties(
                PropertyFactory.lineColor(trafficMagnitudeColorExpression()),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.85f),
            )
            // Under the circle/count layers, not above — a wide, saturated line shouldn't cover
            // the tap-target dot it belongs to.
            style.addLayerBelow(layer, "traffic-layer")
        }
    }

    private fun trafficMagnitudeColorExpression(): Expression = Expression.match(
        Expression.get("magnitude"),
        Expression.color(TRAFFIC_MINOR),
        Expression.stop(1, Expression.color(TRAFFIC_MINOR)),
        Expression.stop(2, Expression.color(TRAFFIC_MODERATE)),
        Expression.stop(3, Expression.color(TRAFFIC_MAJOR)),
        Expression.stop(4, Expression.color(TRAFFIC_CLOSURE)),
    )

    private val layerIdsByMapLayer: Map<MapLayer, List<String>> = mapOf(
        MapLayer.FLIGHTS to listOf("flights-layer"),
        MapLayer.EARTHQUAKES to listOf("earthquakes-layer"),
        MapLayer.FIRES to listOf("fires-layer"),
        MapLayer.WEATHER to listOf("weather-layer"),
        MapLayer.CONFLICTS to listOf("conflicts-layer"),
        MapLayer.MARITIME to listOf("ports-layer", "chokepoints-layer", "ships-layer"),
        MapLayer.SATELLITES to listOf("satellites-layer"),
        MapLayer.NEWS to listOf("news-layer"),
        MapLayer.CYBER_ATTACKS to listOf("cyber-attacks-layer", "cyber-attacks-pulse-layer"),
        MapLayer.CCTV to listOf("cctv-clusters", "cctv-cluster-count", "cctv-unclustered"),
        MapLayer.OSINT to listOf("osint-layer"),
        MapLayer.TRAFFIC to listOf("traffic-layer", "traffic-lines-layer"),
    )

    fun setLayerVisible(layer: MapLayer, visible: Boolean) {
        val visibility = PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
        layerIdsByMapLayer[layer]?.forEach { layerId ->
            style.getLayer(layerId)?.setProperties(visibility)
        }
    }

    private fun updateSource(sourceId: String, features: List<Feature>) {
        val collection = FeatureCollection.fromFeatures(features)
        val existing = style.getSource(sourceId) as? GeoJsonSource
        if (existing != null) {
            existing.setGeoJson(collection)
        } else {
            style.addSource(GeoJsonSource(sourceId, collection))
        }
    }

    private fun ensureCircleLayer(
        layerId: String,
        sourceId: String,
        color: PropertyValue<*>,
        radius: PropertyValue<*>,
    ) {
        if (style.getLayer(layerId) != null) return
        val layer = CircleLayer(layerId, sourceId).withProperties(
            color,
            radius,
            PropertyFactory.circleOpacity(0.85f),
            PropertyFactory.circleStrokeWidth(1f),
            PropertyFactory.circleStrokeColor("#0A0E14".toColorInt()),
        )
        style.addLayer(layer)
    }

    private val registeredImages = mutableSetOf<String>()

    /** Registers a tinted bitmap under [name] once — [IconBitmaps] renders a fresh bitmap per
     * (drawable, color) pair, so this is skipped on every later poll once it's in the style. */
    private fun ensureImage(name: String, resId: Int, tint: Int) {
        if (!registeredImages.add(name)) return
        style.addImage(name, IconBitmaps.render(context, resId, tint))
    }

    /** Client-side relief shading from a public, keyless elevation dataset (AWS Terrain Tiles,
     * Mapzen's old "Terrarium" PNG encoding — still mirrored and free years after Mapzen itself
     * shut down) — no API key, no backend, just another raster-dem tile source alongside the
     * existing Esri imagery/labels pattern. Gives the map real topographic depth (mountains,
     * valleys, coastlines) rather than flat-colored land, especially once combined with camera
     * tilt (see MapScreen's "Vue 3D" toggle) — a flat top-down view still shows the shading, just
     * less dramatically, the same way a paper relief map reads even without tilting it.
     *
     * Inserted below "building": on the liberty style this lands it above roads/land polygons but
     * below the buildings/building-3d extrusions and labels that should stay crisp on top of it.
     * If a future style swap ever drops that layer id, addLayerBelow would throw — style.getLayer
     * guards it so terrain just doesn't get added rather than crashing the whole style load. */
    private fun ensureTerrain() {
        val demSource = RasterDemSource(
            "terrain-dem",
            TileSet("2.1.0", "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png").apply {
                encoding = "terrarium"
            },
            256,
        )
        style.addSource(demSource)
        // hillshadeShadowColor/HighlightColor only take an Expression or String[] (per-light-source
        // colors for the "multidirectional" method) — no plain @ColorInt overload, unlike
        // hillshadeAccentColor just below. Expression.color(Int) is the same pattern already used
        // for circleColor's severity stops above.
        val hillshade = HillshadeLayer("hillshade-layer", "terrain-dem").withProperties(
            PropertyFactory.hillshadeExaggeration(0.6f),
            PropertyFactory.hillshadeShadowColor(Expression.color("#2a2a3a".toColorInt())),
            PropertyFactory.hillshadeHighlightColor(Expression.color("#ffffff".toColorInt())),
            PropertyFactory.hillshadeAccentColor("#4a4a5a".toColorInt()),
        )
        if (style.getLayer("building") != null) {
            style.addLayerBelow(hillshade, "building")
        } else {
            style.addLayer(hillshade)
        }
    }

    private fun ensureSymbolLayer(
        layerId: String,
        sourceId: String,
        iconImage: PropertyValue<*>,
        iconSize: PropertyValue<*>,
        iconRotate: PropertyValue<*>? = null,
        filter: Expression? = null,
    ) {
        if (style.getLayer(layerId) != null) return
        val properties = buildList {
            add(iconImage)
            add(iconSize)
            add(PropertyFactory.iconAllowOverlap(true))
            add(PropertyFactory.iconIgnorePlacement(true))
            add(PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP))
            iconRotate?.let { add(it) }
        }
        val layer = SymbolLayer(layerId, sourceId).withProperties(*properties.toTypedArray())
        filter?.let { layer.setFilter(it) }
        style.addLayer(layer)
    }

    /** Same clustering strategy as [setCctv] originally introduced (supercluster via MapLibre's
     * built-in `GeoJsonOptions.withCluster`) — extracted once it was needed for a third dense
     * layer (flights, satellites) so all three share identical cluster-circle/count styling. */
    private fun ensureClusteredSource(sourceId: String, features: List<Feature>) {
        val collection = FeatureCollection.fromFeatures(features)
        val existing = style.getSource(sourceId) as? GeoJsonSource
        if (existing != null) {
            existing.setGeoJson(collection)
        } else {
            val options = GeoJsonOptions()
                .withCluster(true)
                .withClusterMaxZoom(13)
                .withClusterRadius(50)
            style.addSource(GeoJsonSource(sourceId, collection, options))
        }
    }

    private fun ensureClusterCircleLayers(sourceId: String, clustersLayerId: String, countLayerId: String, dotColor: Int) {
        if (style.getLayer(clustersLayerId) == null) {
            val clusters = CircleLayer(clustersLayerId, sourceId).withProperties(
                PropertyFactory.circleColor(dotColor),
                // Bumped up and made zoom-aware on top of the existing point_count step: at a
                // whole-country zoom (e.g. seeing all of France) a flat 14-24px bubble reads as
                // a near-invisible speck, so scale everything up the further out you are.
                PropertyFactory.circleRadius(
                    Expression.product(
                        Expression.step(
                            Expression.toNumber(Expression.get("point_count")),
                            Expression.literal(16f),
                            Expression.stop(50, Expression.literal(20f)),
                            Expression.stop(500, Expression.literal(26f)),
                        ),
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.zoom(),
                            Expression.stop(3f, Expression.literal(1.6f)),
                            Expression.stop(8f, Expression.literal(1.2f)),
                            Expression.stop(13f, Expression.literal(1f)),
                        ),
                    )
                ),
                PropertyFactory.circleOpacity(0.85f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                PropertyFactory.circleStrokeOpacity(0.9f),
            )
            clusters.setFilter(Expression.has("point_count"))
            style.addLayer(clusters)
        }
        if (style.getLayer(countLayerId) == null) {
            val counts = SymbolLayer(countLayerId, sourceId).withProperties(
                PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(android.graphics.Color.WHITE),
                PropertyFactory.textHaloColor(android.graphics.Color.BLACK),
                PropertyFactory.textHaloWidth(1f),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textAllowOverlap(true),
            )
            counts.setFilter(Expression.has("point_count"))
            style.addLayer(counts)
        }
    }

    /** Zoom level at which tapping this cluster feature (from any clustered [sourceId]) would
     * split it apart, or null if the source isn't ready yet or the feature isn't a cluster. */
    fun clusterExpansionZoom(sourceId: String, feature: Feature): Int? {
        val source = style.getSource(sourceId) as? GeoJsonSource ?: return null
        return runCatching { source.getClusterExpansionZoom(feature) }.getOrNull()
    }

    private inline fun feature(lng: Double, lat: Double, block: Feature.() -> Unit): Feature =
        Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply(block)
}

private fun String.toColorInt(): Int = android.graphics.Color.parseColor(this)
