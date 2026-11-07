package app.frame.studio.effects

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import app.frame.studio.domain.CanvasFill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Places a clip on a [width]×[height] canvas in one GL pass: its fitted (or covering, for Fill)
 * box, moved to ([x], [y]) (centre, 0..1, y down), scaled by [scale] and turned [rotation]
 * degrees clockwise — what the user set with their fingers. Around it: a blurred, dimmed copy of
 * the clip ([CanvasFill.BLUR]) or black. Pixels inside the clip are a plain sample.
 */
@OptIn(UnstableApi::class)
class CanvasPlaceEffect(
    private val width: Int,
    private val height: Int,
    private val fill: CanvasFill,
    private val x: Float = 0.5f,
    private val y: Float = 0.5f,
    private val scale: Float = 1f,
    private val rotation: Float = 0f,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = Program(useHdr, this)

    private class Program(useHdr: Boolean, private val e: CanvasPlaceEffect) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {
        private val program: GlProgram = try {
            GlProgram(VERTEX, FRAGMENT)
        } catch (ex: GlUtil.GlException) {
            throw VideoFrameProcessingException(ex)
        }

        init {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val clip = inputWidth.toFloat() / inputHeight.coerceAtLeast(1)
            val canvas = e.width.toFloat() / e.height.coerceAtLeast(1)
            val wider = clip > canvas
            val fit = if (wider) floatArrayOf(1f, canvas / clip) else floatArrayOf(clip / canvas, 1f)
            val cover = if (wider) floatArrayOf(clip / canvas, 1f) else floatArrayOf(1f, canvas / clip)
            val base = if (e.fill == CanvasFill.FILL) cover else fit
            // Box size in canvas pixels.
            program.setFloatsUniformIfPresent("uBoxPx", floatArrayOf(base[0] * e.scale * e.width, base[1] * e.scale * e.height))
            program.setFloatsUniformIfPresent("uCanvasPx", floatArrayOf(e.width.toFloat(), e.height.toFloat()))
            // GL texture space is y-up; the editor's y is down.
            program.setFloatsUniformIfPresent("uCenter", floatArrayOf(e.x, 1f - e.y))
            // A clockwise turn on screen is negative in y-up space; sampling uses the inverse.
            val a = (e.rotation * PI / 180.0).toFloat()
            program.setFloatsUniformIfPresent("uRot", floatArrayOf(cos(a), sin(a)))
            program.setFloatsUniformIfPresent("uCover", cover)
            program.setFloatsUniformIfPresent("uBlur", floatArrayOf(if (e.fill == CanvasFill.BLUR) 1f else 0f))
            program.setFloatsUniformIfPresent("uTexel", floatArrayOf(1f / inputWidth.coerceAtLeast(1), 1f / inputHeight.coerceAtLeast(1)))
            return Size(e.width, e.height)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (ex: GlUtil.GlException) {
                throw VideoFrameProcessingException(ex, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                program.delete()
            } catch (ex: GlUtil.GlException) {
                throw VideoFrameProcessingException(ex)
            }
        }
    }

    private companion object {
        const val VERTEX = """
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

        const val FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform vec2 uBoxPx;
uniform vec2 uCanvasPx;
uniform vec2 uCenter;
uniform vec2 uRot;
uniform vec2 uCover;
uniform float uBlur;
uniform vec2 uTexel;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = vTexSamplingCoord;
  // Into the clip's own space: offset from its centre in pixels, un-rotated, then 0..1.
  vec2 d = (uv - uCenter) * uCanvasPx;
  vec2 r = vec2(uRot.x * d.x - uRot.y * d.y, uRot.y * d.x + uRot.x * d.y);
  vec2 c = r / uBoxPx + 0.5;
  if (c.x >= 0.0 && c.x <= 1.0 && c.y >= 0.0 && c.y <= 1.0) {
    gl_FragColor = texture2D(uTexSampler, c);
    return;
  }
  if (uBlur < 0.5) {
    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    return;
  }
  // Background: the clip covering the canvas, blurred (24-tap Vogel disk) and dimmed.
  vec2 b = (uv - 0.5) / uCover + 0.5;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 24; i++) {
    float fi = float(i);
    float rr = sqrt((fi + 0.5) / 24.0) * 0.045;
    float a = fi * 2.39996;
    acc += texture2D(uTexSampler, clamp(b + vec2(cos(a), sin(a)) * rr, uTexel, 1.0 - uTexel)).rgb;
  }
  gl_FragColor = vec4(acc / 24.0 * 0.62, 1.0);
}
"""
    }
}
