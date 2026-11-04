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

/**
 * Vignette (darkened corners) and film grain in a single GPU pass. Applied per clip after the
 * frame is fitted to the canvas, so the vignette follows the output frame's shape.
 */
@OptIn(UnstableApi::class)
class TextureLookEffect(
    /** 0..1 */
    private val vignette: Float,
    /** 0..1 */
    private val grain: Float,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        Program(useHdr, vignette.coerceIn(0f, 1f), grain.coerceIn(0f, 1f))

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = vignette <= 0f && grain <= 0f

    private class Program(useHdr: Boolean, vignette: Float, grain: Float) :
        BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

        private val program: GlProgram = try {
            GlProgram(VERTEX, FRAGMENT)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        init {
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            program.setFloatUniform("uVignette", vignette)
            program.setFloatUniform("uGrain", grain)
            program.setFloatUniform("uAspect", 1f)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            program.setFloatUniform("uAspect", inputWidth.toFloat() / inputHeight.coerceAtLeast(1))
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                // Small, wrapping seed keeps the grain moving without float precision issues.
                program.setFloatUniform("uSeed", ((presentationTimeUs / 33_333) % 97).toFloat() / 97f)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
    }

    companion object {
        private const val VERTEX = """
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

        private const val FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform float uVignette;
uniform float uGrain;
uniform float uAspect;
uniform float uSeed;
varying vec2 vTexSamplingCoord;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

void main() {
  vec4 color = texture2D(uTexSampler, vTexSamplingCoord);
  vec2 p = vTexSamplingCoord - 0.5;
  p.x *= uAspect;
  float d = length(p) / length(vec2(0.5 * uAspect, 0.5));
  float v = smoothstep(0.4, 1.05, d);
  color.rgb *= 1.0 - uVignette * v * 0.85;
  float n = hash(vTexSamplingCoord * 731.0 + uSeed * 17.0) - 0.5;
  color.rgb += n * uGrain * 0.14;
  gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.a);
}
"""
    }
}
