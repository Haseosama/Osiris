package com.osiris.app.globe

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Drag-to-rotate, pinch-to-zoom touch handling around [GlobeRenderer] — the view-layer half of
 * the globe; [GlobeRenderer] is the render-thread half. Continuous render mode (rather than
 * [GLSurfaceView.RENDERMODE_WHEN_DIRTY] plus manual `requestRender()` calls) since the globe auto-
 * rotates on its own even with no touch input at all, so something needs to be redrawing every
 * frame regardless.
 */
class GlobeSurfaceView(context: Context) : GLSurfaceView(context) {

    val renderer = GlobeRenderer()

    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.zoom = (renderer.zoom / detector.scaleFactor).coerceIn(0.5f, 3f)
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
                renderer.autoRotate = false
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Resumes the idle spin as soon as the finger lifts — a deliberate "let go and it
                // keeps drifting" feel rather than a physics-based momentum fling, much simpler
                // to get right without it looking twitchy.
                renderer.autoRotate = true
            }
        }
        return true
    }

    private companion object {
        const val DRAG_TO_DEGREES = 0.35f
    }
}
