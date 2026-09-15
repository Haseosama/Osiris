package com.osiris.app.globe

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/** Below this [GlobeRenderer.zoom] value (smaller = closer, see its own doc), pinching in further
 * hands off to the flat map instead of continuing to zoom the sphere — a custom OpenGL globe has
 * no street-level detail to zoom into (it's one whole-Earth texture, see [EarthTextureLoader]), so
 * "zoom in past a point" reads much more naturally as "switch to the real map" than as the globe
 * just getting a blurrier and blurrier close-up of the same low-res image. Kept below
 * [com.osiris.app.globe.FLY_TO_ZOOM] (where the location button and search results land) so that
 * arriving at a place still leaves room to pinch in a little on the globe itself before the flat
 * map takes over. */
const val ZOOM_TO_FLAT_MAP_THRESHOLD = 0.32f

/** Pinch limits. The floor is just shy of [ZOOM_TO_FLAT_MAP_THRESHOLD]: crossing the threshold
 * hands off, so the globe is never left sitting closer than this — and the camera-distance clamp
 * in [GlobeRenderer.onDrawFrame] guarantees even this floor can't push the near plane into the
 * sphere on any screen shape. */
private const val MIN_ZOOM = 0.3f
private const val MAX_ZOOM = 3f


/**
 * Drag-to-rotate, pinch-to-zoom touch handling around [GlobeRenderer] — the view-layer half of
 * the globe; [GlobeRenderer] is the render-thread half. Continuous render mode (rather than
 * [GLSurfaceView.RENDERMODE_WHEN_DIRTY] plus manual `requestRender()` calls after every touch
 * delta) since this is a small, occasional secondary screen where the simplicity is worth more
 * than the battery saving a dirty-only mode would buy.
 */
class GlobeSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer = GlobeRenderer()

    /** Fired once when a pinch crosses [ZOOM_TO_FLAT_MAP_THRESHOLD] — [GlobeScreen] wires this to
     * navigate to the flat map centered on [GlobeRenderer.centerLatLng]. The [hasHandedOff] guard
     * exists because a single pinch gesture reports many onScale() calls in a row; without it,
     * every one of them past the threshold would re-fire the callback before navigation has even
     * had a chance to leave this screen. */
    var onZoomedIn: ((lat: Double, lng: Double) -> Unit)? = null
    private var hasHandedOff = false

    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.zoom = (renderer.zoom / detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                // scaleFactor > 1 is fingers moving apart, i.e. zooming IN (zoom divides by it, and
                // smaller zoom = closer). Without this check, merely being below the threshold —
                // which the location/search fly-to now deliberately leaves the globe at — would make
                // ANY pinch, including one zooming back OUT, teleport the user to the flat map.
                if (!hasHandedOff && detector.scaleFactor > 1f && renderer.zoom <= ZOOM_TO_FLAT_MAP_THRESHOLD) {
                    hasHandedOff = true
                    val (lat, lng) = renderer.centerLatLng()
                    onZoomedIn?.invoke(lat, lng)
                }
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    renderer.rotationY += dx * DRAG_TO_DEGREES
                    renderer.rotationX = (renderer.rotationX + dy * DRAG_TO_DEGREES).coerceIn(-90f, 90f)
                    lastX = event.x
                    lastY = event.y
                }
            }
            else -> Unit
        }
        return true
    }

    private companion object {
        const val DRAG_TO_DEGREES = 0.35f
    }
}
