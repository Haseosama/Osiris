package com.osiris.app.map

import android.content.Context
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
import com.osiris.app.data.model.WeatherEvent
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private val FLIGHT_COMMERCIAL = "#00E5FF".toColorInt()
private val FLIGHT_PRIVATE = "#FFD54A".toColorInt()
private val FLIGHT_JET = "#E040FB".toColorInt()
private val FLIGHT_MILITARY = "#4CD97B".toColorInt()

private val SEVERITY_LOW = "#4CD97B".toColorInt()
private val SEVERITY_MEDIUM = "#FFB020".toColorInt()
private val SEVERITY_HIGH = "#FF5252".toColorInt()
private val SEVERITY_WAR = "#B00020".toColorInt()

private val FIRE_COLOR = "#FF7A1A".toColorInt()

private val PORT_CONTAINER = "#00E5FF".toColorInt()
private val PORT_ENERGY = "#FFB020".toColorInt()
private val PORT_NAVAL = "#FF5252".toColorInt()
private val SHIP_COLOR = "#8AE6C8".toColorInt()

private val SAT_COMMS = "#00E676".toColorInt()
private val SAT_NAVIGATION = "#448AFF".toColorInt()
private val SAT_EARTH_OBS = "#90EE90".toColorInt()
private val SAT_MILITARY = "#FF3D3D".toColorInt()
private val SAT_SCIENCE = "#FFD700".toColorInt()
private val SAT_OTHER = "#00E5FF".toColorInt()

private val NEWS_COLOR = "#00E5FF".toColorInt()

private val CCTV_COLOR = "#00E5FF".toColorInt()

/**
 * Adds/updates one GeoJSON source + circle layer per [MapLayer] on the given MapLibre [style].
 * Data is pushed in via `set*` whenever a repository poll completes; visibility is toggled
 * independently so a layer can be hidden without losing its cached data.
 */
class LayersController(private val style: Style, private val context: Context) {

    fun setFlights(markers: List<FlightMarker>) {
        ensureImage("flight-commercial", R.drawable.ic_plane, FLIGHT_COMMERCIAL)
        ensureImage("flight-private", R.drawable.ic_plane, FLIGHT_PRIVATE)
        ensureImage("flight-jet", R.drawable.ic_plane, FLIGHT_JET)
        ensureImage("flight-military", R.drawable.ic_plane, FLIGHT_MILITARY)

        val features = markers.mapIndexed { index, marker ->
            feature(marker.flight.lng, marker.flight.lat) {
                addNumberProperty("idx", index)
                addStringProperty("category", marker.category.name)
                marker.flight.callsign?.let { addStringProperty("callsign", it) }
                marker.flight.heading?.let { addNumberProperty("heading", it) }
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
            iconSize = 0.7f,
            iconRotate = PropertyFactory.iconRotate(Expression.get("heading")),
        )
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
            iconSize = 0.45f,
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

    fun setMaritime(maritime: MaritimeResponse) {
        ensureImage("port-container", R.drawable.ic_anchor, PORT_CONTAINER)
        ensureImage("port-energy", R.drawable.ic_anchor, PORT_ENERGY)
        ensureImage("port-naval", R.drawable.ic_anchor, PORT_NAVAL)

        val portFeatures = maritime.ports.mapIndexed { index, port ->
            feature(port.lng, port.lat) {
                addNumberProperty("idx", index)
                addStringProperty("name", port.name)
                addStringProperty("type", port.type)
            }
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
            iconSize = 0.55f,
        )

        val chokepointFeatures = maritime.chokepoints.mapIndexed { index, choke ->
            feature(choke.lng, choke.lat) {
                addNumberProperty("idx", index)
                addStringProperty("name", choke.name)
                addStringProperty("risk", choke.risk)
            }
        }
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

        ensureImage("ship-icon", R.drawable.ic_boat, SHIP_COLOR)

        val shipFeatures = maritime.ships.mapIndexed { index, ship ->
            feature(ship.lng, ship.lat) {
                addNumberProperty("idx", index)
                ship.name?.let { addStringProperty("name", it) }
                ship.type?.let { addStringProperty("type", it) }
                ship.heading?.let { addNumberProperty("heading", it) }
            }
        }
        updateSource("ships-source", shipFeatures)
        ensureSymbolLayer(
            layerId = "ships-layer",
            sourceId = "ships-source",
            iconImage = PropertyFactory.iconImage("ship-icon"),
            iconSize = 0.5f,
            iconRotate = PropertyFactory.iconRotate(Expression.get("heading")),
        )
    }

    fun setSatellites(satellites: List<Satellite>) {
        ensureImage("sat-comms", R.drawable.ic_satellite, SAT_COMMS)
        ensureImage("sat-navigation", R.drawable.ic_satellite, SAT_NAVIGATION)
        ensureImage("sat-earth_obs", R.drawable.ic_satellite, SAT_EARTH_OBS)
        ensureImage("sat-military", R.drawable.ic_satellite, SAT_MILITARY)
        ensureImage("sat-science", R.drawable.ic_satellite, SAT_SCIENCE)
        ensureImage("sat-other", R.drawable.ic_satellite, SAT_OTHER)

        val features = satellites.mapIndexed { index, sat ->
            feature(sat.lng, sat.lat) {
                addNumberProperty("idx", index)
                addStringProperty("name", sat.name)
                addStringProperty("category", sat.category ?: "other")
                sat.mission?.let { addStringProperty("mission", it) }
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
            iconSize = 0.5f,
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
        val features = attacks.map { attack ->
            // A curved arc, not a straight line — see ArcMath — echoing the flying arcs the
            // web app animates for these. The MapViewModel-driven pulse layer below travels
            // along this exact same curve.
            val curve = ArcMath.curve(attack.srcLng, attack.srcLat, attack.dstLng, attack.dstLat)
            val line = LineString.fromLngLats(curve.map { (lng, lat) -> Point.fromLngLat(lng, lat) })
            Feature.fromGeometry(line).apply {
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

    /**
     * ~17k points in one source, so this uses MapLibre's built-in clustering instead of the
     * flat [ensureCircleLayer] helper: a "clusters" circle layer sized by point_count, a text
     * layer with the count, and an "unclustered" layer for individual cameras once zoomed in.
     */
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
        val collection = FeatureCollection.fromFeatures(features)
        val existingSource = style.getSource("cctv-source") as? GeoJsonSource
        if (existingSource != null) {
            existingSource.setGeoJson(collection)
        } else {
            val options = GeoJsonOptions()
                .withCluster(true)
                .withClusterMaxZoom(13)
                .withClusterRadius(50)
            style.addSource(GeoJsonSource("cctv-source", collection, options))
        }

        if (style.getLayer("cctv-clusters") == null) {
            val clusters = CircleLayer("cctv-clusters", "cctv-source").withProperties(
                PropertyFactory.circleColor(CCTV_COLOR),
                PropertyFactory.circleRadius(
                    Expression.step(
                        Expression.toNumber(Expression.get("point_count")),
                        Expression.literal(14f),
                        Expression.stop(50, Expression.literal(18f)),
                        Expression.stop(500, Expression.literal(24f)),
                    )
                ),
                PropertyFactory.circleOpacity(0.75f),
            )
            clusters.setFilter(Expression.has("point_count"))
            style.addLayer(clusters)
        }
        if (style.getLayer("cctv-cluster-count") == null) {
            val counts = SymbolLayer("cctv-cluster-count", "cctv-source").withProperties(
                PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(android.graphics.Color.WHITE),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textAllowOverlap(true),
            )
            counts.setFilter(Expression.has("point_count"))
            style.addLayer(counts)
        }
        if (style.getLayer("cctv-unclustered") == null) {
            val points = SymbolLayer("cctv-unclustered", "cctv-source").withProperties(
                PropertyFactory.iconImage("cctv-camera"),
                PropertyFactory.iconSize(0.45f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
            points.setFilter(Expression.not(Expression.has("point_count")))
            style.addLayer(points)
        }
    }

    /** Zoom level at which tapping this CCTV cluster feature would split it apart, or null if
     * the source isn't ready yet or the feature isn't actually a cluster. */
    fun cctvClusterExpansionZoom(feature: Feature): Int? {
        val source = style.getSource("cctv-source") as? GeoJsonSource ?: return null
        return runCatching { source.getClusterExpansionZoom(feature) }.getOrNull()
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

    private fun ensureSymbolLayer(
        layerId: String,
        sourceId: String,
        iconImage: PropertyValue<*>,
        iconSize: Float,
        iconRotate: PropertyValue<*>? = null,
    ) {
        if (style.getLayer(layerId) != null) return
        val properties = buildList {
            add(iconImage)
            add(PropertyFactory.iconSize(iconSize))
            add(PropertyFactory.iconAllowOverlap(true))
            add(PropertyFactory.iconIgnorePlacement(true))
            add(PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP))
            iconRotate?.let { add(it) }
        }
        val layer = SymbolLayer(layerId, sourceId).withProperties(*properties.toTypedArray())
        style.addLayer(layer)
    }

    private inline fun feature(lng: Double, lat: Double, block: Feature.() -> Unit): Feature =
        Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply(block)
}

private fun String.toColorInt(): Int = android.graphics.Color.parseColor(this)
