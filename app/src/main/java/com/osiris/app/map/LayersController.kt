package com.osiris.app.map

import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.WeatherEvent
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private val FLIGHT_COMMERCIAL = "#00E5FF".toColorInt()
private val FLIGHT_PRIVATE = "#FFD54A".toColorInt()
private val FLIGHT_JET = "#E040FB".toColorInt()
private val FLIGHT_MILITARY = "#FF5252".toColorInt()

private val SEVERITY_LOW = "#4CD97B".toColorInt()
private val SEVERITY_MEDIUM = "#FFB020".toColorInt()
private val SEVERITY_HIGH = "#FF5252".toColorInt()
private val SEVERITY_WAR = "#B00020".toColorInt()

private val FIRE_COLOR = "#FF7A1A".toColorInt()

/**
 * Adds/updates one GeoJSON source + circle layer per [MapLayer] on the given MapLibre [style].
 * Data is pushed in via `set*` whenever a repository poll completes; visibility is toggled
 * independently so a layer can be hidden without losing its cached data.
 */
class LayersController(private val style: Style) {

    fun setFlights(markers: List<FlightMarker>) {
        val features = markers.map { marker ->
            feature(marker.flight.lng, marker.flight.lat) {
                addStringProperty("category", marker.category.name)
                marker.flight.callsign?.let { addStringProperty("callsign", it) }
            }
        }
        updateSource("flights-source", features)
        ensureCircleLayer(
            layerId = "flights-layer",
            sourceId = "flights-source",
            color = PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("category"),
                    Expression.color(FLIGHT_COMMERCIAL),
                    Expression.stop("COMMERCIAL", Expression.color(FLIGHT_COMMERCIAL)),
                    Expression.stop("PRIVATE", Expression.color(FLIGHT_PRIVATE)),
                    Expression.stop("JET", Expression.color(FLIGHT_JET)),
                    Expression.stop("MILITARY", Expression.color(FLIGHT_MILITARY)),
                )
            ),
            radius = PropertyFactory.circleRadius(4f),
        )
    }

    fun setEarthquakes(earthquakes: List<Earthquake>) {
        val features = earthquakes.map { quake ->
            feature(quake.lng, quake.lat) {
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
        val features = fires.map { fire ->
            feature(fire.lng, fire.lat) {
                fire.title?.let { addStringProperty("title", it) }
            }
        }
        updateSource("fires-source", features)
        ensureCircleLayer(
            layerId = "fires-layer",
            sourceId = "fires-source",
            color = PropertyFactory.circleColor(FIRE_COLOR),
            radius = PropertyFactory.circleRadius(3.5f),
        )
    }

    fun setWeatherEvents(events: List<WeatherEvent>) {
        val features = events.map { event ->
            feature(event.lng, event.lat) {
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
        val features = zones.map { zone ->
            feature(zone.lng, zone.lat) {
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

    fun setLayerVisible(layer: MapLayer, visible: Boolean) {
        val layerId = when (layer) {
            MapLayer.FLIGHTS -> "flights-layer"
            MapLayer.EARTHQUAKES -> "earthquakes-layer"
            MapLayer.FIRES -> "fires-layer"
            MapLayer.WEATHER -> "weather-layer"
            MapLayer.CONFLICTS -> "conflicts-layer"
        }
        style.getLayer(layerId)?.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
        )
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

    private inline fun feature(lng: Double, lat: Double, block: Feature.() -> Unit): Feature =
        Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply(block)
}

private fun String.toColorInt(): Int = android.graphics.Color.parseColor(this)
