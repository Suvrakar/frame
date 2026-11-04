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
 * Tone grading (highlights, shadows, vibrance, split toning) plus vignette and grain, in a single
 * GPU pass. Runs after the clip's colour matrix and after the frame is fitted to the canvas.
 * [GradeMath.tone] is the CPU mirror of the tone part; keep them in sync.
 */
@OptIn(UnstableApi::class)
class GradeEffect(private val params: GradeParams) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = Program(useHdr, params)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = params.isNone

    private class Program(useHdr: Boolean, p: GradeParams) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

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
            program.setFloatUniform("uVignette", p.vignette.coerceIn(0f, 1f))
            program.setFloatUniform("uGrain", p.grain.coerceIn(0f, 1f))
            program.setFloatUniform("uHighlights", p.highlights)
            program.setFloatUniform("uShadows", p.shadows)
            program.setFloatUniform("uVibrance", p.vibrance)
            program.setFloatsUniform("uShadowTint", p.shadowTint)
            program.setFloatsUniform("uHighlightTint", p.highlightTint)
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
uniform float uHighlights;
uniform float uShadows;
uniform float uVibrance;
uniform vec3 uShadowTint;
uniform vec3 uHighlightTint;
varying vec2 vTexSamplingCoord;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

void main() {
  vec4 color = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 c = color.rgb;

  // Tone: weights for dark and bright areas.
  float l = dot(c, LUMA);
  float sw = 1.0 - smoothstep(0.0, 0.55, l);
  float hw = smoothstep(0.45, 1.0, l);
  c += vec3(uShadows * 0.28 * sw + uHighlights * 0.28 * hw);
  c += (uShadowTint * sw + uHighlightTint * hw) * 0.3;

  // Vibrance: boost muted colours more than already-saturated ones.
  float l2 = dot(c, LUMA);
  float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
  c = mix(vec3(l2), c, 1.0 + uVibrance * (1.0 - sat));
  c = clamp(c, 0.0, 1.0);

  // Vignette and grain.
  vec2 p = vTexSamplingCoord - 0.5;
  p.x *= uAspect;
  float d = length(p) / length(vec2(0.5 * uAspect, 0.5));
  c *= 1.0 - uVignette * smoothstep(0.4, 1.05, d) * 0.85;
  c += (hash(vTexSamplingCoord * 731.0 + uSeed * 17.0) - 0.5) * uGrain * 0.14;

  gl_FragColor = vec4(clamp(c, 0.0, 1.0), color.a);
}
"""
    }
}
