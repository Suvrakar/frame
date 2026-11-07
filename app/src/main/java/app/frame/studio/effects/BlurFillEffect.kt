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
 * Places a clip on a [width]×[height] canvas the way social apps do: the whole clip, sharp, over a
 * blurred and dimmed copy of itself enlarged to cover the canvas — no black bars and nothing cut
 * off. One GL pass; pixels inside the clip are a plain copy, so a clip that already matches the
 * canvas costs nothing extra.
 */
@OptIn(UnstableApi::class)
class BlurFillEffect(private val width: Int, private val height: Int) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = Program(useHdr, width, height)

    private class Program(useHdr: Boolean, private val width: Int, private val height: Int) :
        BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

        private val program: GlProgram = try {
            GlProgram(VERTEX, FRAGMENT)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        init {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val clip = inputWidth.toFloat() / inputHeight.coerceAtLeast(1)
            val canvas = width.toFloat() / height.coerceAtLeast(1)
            // Sizes in canvas units (canvas = 1×1): the fitted clip, and the copy that covers it.
            val content = if (clip > canvas) floatArrayOf(1f, canvas / clip) else floatArrayOf(clip / canvas, 1f)
            val cover = if (clip > canvas) floatArrayOf(clip / canvas, 1f) else floatArrayOf(1f, canvas / clip)
            program.setFloatsUniformIfPresent("uContent", content)
            program.setFloatsUniformIfPresent("uCover", cover)
            program.setFloatsUniformIfPresent("uTexel", floatArrayOf(1f / inputWidth.coerceAtLeast(1), 1f / inputHeight.coerceAtLeast(1)))
            return Size(width, height)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
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

        /** Blur: 24-tap Vogel disk (even coverage, no ring artefacts), then dimmed. */
        const val FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform vec2 uContent;
uniform vec2 uCover;
uniform vec2 uTexel;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 uv = vTexSamplingCoord;
  vec2 c = (uv - 0.5) / uContent + 0.5;
  if (c.x >= 0.0 && c.x <= 1.0 && c.y >= 0.0 && c.y <= 1.0) {
    gl_FragColor = texture2D(uTexSampler, c);
    return;
  }
  vec2 b = (uv - 0.5) / uCover + 0.5;
  float radius = 0.045;
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 24; i++) {
    float fi = float(i);
    float r = sqrt((fi + 0.5) / 24.0) * radius;
    float a = fi * 2.39996;
    vec2 o = vec2(cos(a), sin(a)) * r;
    acc += texture2D(uTexSampler, clamp(b + o, uTexel, 1.0 - uTexel)).rgb;
  }
  gl_FragColor = vec4(acc / 24.0 * 0.62, 1.0);
}
"""
    }
}
