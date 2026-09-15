package com.osiris.app.globe

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.GLSurfaceView

private const val VERTEX_SHADER = """
    uniform mat4 uMVPMatrix;
    uniform mat4 uModelMatrix;
    attribute vec4 aPosition;
    attribute vec3 aNormal;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    varying vec3 vNormal;
    void main() {
        gl_Position = uMVPMatrix * aPosition;
        vTexCoord = aTexCoord;
        vNormal = (uModelMatrix * vec4(aNormal, 0.0)).xyz;
    }
"""

private const val FRAGMENT_SHADER = """
    precision mediump float;
    uniform sampler2D uTexture;
    uniform vec3 uLightDir;
    varying vec2 vTexCoord;
    varying vec3 vNormal;
    void main() {
        vec3 normal = normalize(vNormal);
        float diffuse = max(dot(normal, normalize(uLightDir)), 0.0);
        // Ambient floor keeps the night side dimly visible (a lit globe UI, not a physically
        // accurate day/night simulation) rather than crushing it to pure black.
        float lighting = 0.35 + diffuse * 0.75;
        vec4 texColor = texture2D(uTexture, vTexCoord);
        gl_FragColor = vec4(texColor.rgb * lighting, texColor.a);
    }
"""

/**
 * Renders [SphereMesh] textured with whatever [EarthTextureLoader] hands it, lit by one fixed
 * directional light for a bit of 3D shading rather than a flat-lit texture. [rotationX]/
 * [rotationY]/[zoom]/[autoRotate] are the public knobs [GlobeSurfaceView]'s touch handling drives
 * — `@Volatile` since they're written from the UI thread (touch events) and read from the GL
 * thread ([onDrawFrame] runs on a dedicated GLThread, not Main), with no stronger synchronization
 * needed: a torn read of a single float mid-drag is, at worst, one frame's rotation being a touch
 * blend of old/new, invisible in practice at 60fps.
 */
class GlobeRenderer : GLSurfaceView.Renderer {

    @Volatile var rotationX = 0f
    @Volatile var rotationY = 0f
    @Volatile var zoom = 1f
    @Volatile var autoRotate = true

    /** Set from outside (see [GlobeScreen]) once [EarthTextureLoader] finishes; consumed and
     * cleared on the very next [onDrawFrame] since glTexImage2D is itself a GL-thread-only call —
     * this is the hand-off point from whichever thread loaded the bitmap to the GL thread. */
    @Volatile var pendingBitmap: Bitmap? = null

    private lateinit var mesh: SphereMesh
    private var program = 0
    private var textureId = 0

    private var aPositionLoc = 0
    private var aNormalLoc = 0
    private var aTexCoordLoc = 0
    private var uMvpMatrixLoc = 0
    private var uModelMatrixLoc = 0
    private var uTextureLoc = 0
    private var uLightDirLoc = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Near-black "space" backdrop rather than the theme background — this view fills the
        // whole screen, so the backdrop IS the space around the globe.
        GLES20.glClearColor(0.01f, 0.01f, 0.03f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        mesh = SphereMesh()

        program = buildProgram()
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(program, "aNormal")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvpMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(program, "uModelMatrix")
        uTextureLoc = GLES20.glGetUniformLocation(program, "uTexture")
        uLightDirLoc = GLES20.glGetUniformLocation(program, "uLightDir")

        textureId = createPlaceholderTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 1f, 10f)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingBitmap?.let { bitmap ->
            uploadTexture(bitmap)
            pendingBitmap = null
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        if (autoRotate) rotationY += AUTO_ROTATE_DEGREES_PER_FRAME

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val distance = (2.6f * zoom).coerceIn(1.4f, 6f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)

        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        GLES20.glUseProgram(program)

        mesh.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 0, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPositionLoc)

        mesh.normalBuffer.position(0)
        GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, 0, mesh.normalBuffer)
        GLES20.glEnableVertexAttribArray(aNormalLoc)

        mesh.texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, mesh.texCoordBuffer)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)

        GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)
        // Fixed light from the upper-right-front — not tied to rotation, so as the globe spins
        // the "sunlit" side changes, same as it would in reality.
        GLES20.glUniform3f(uLightDirLoc, 0.4f, 0.5f, 1f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureLoc, 0)

        mesh.indexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aNormalLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    private fun createPlaceholderTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        // Single ocean-blue pixel shown until EarthTextureLoader's real texture lands — so the
        // globe is a plain (but genuinely 3D, lit, rotatable) sphere immediately rather than
        // blank/invisible while a multi-MB image downloads over the network.
        val pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        pixel.put(byteArrayOf(18, 54, 94, 255.toByte()))
        pixel.position(0)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel,
        )
        applyTextureParams(mipmapped = false)
        return id
    }

    private fun uploadTexture(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        applyTextureParams(mipmapped = true)
    }

    private fun applyTextureParams(mipmapped: Boolean) {
        val minFilter = if (mipmapped) GLES20.GL_LINEAR_MIPMAP_LINEAR else GLES20.GL_LINEAR
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, minFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // Wraps around the seam at the antimeridian (S = longitude); T (latitude) never wraps —
        // the poles are single points, not a seam to hide.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun buildProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vertexShader)
        GLES20.glAttachShader(prog, fragmentShader)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private companion object {
        const val AUTO_ROTATE_DEGREES_PER_FRAME = 0.12f
    }
}
