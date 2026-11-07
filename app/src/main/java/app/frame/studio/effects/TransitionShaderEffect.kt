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
import app.frame.studio.export.TransitionPlan
import app.frame.studio.export.TransitionWindow

/**
 * Draws every shader transition of a movie (zoom, whip, spin, blur, glitch, pixelate, iris, shake,
 * film burn) in one GL pass on the composed frame. Outside transition windows it is a plain copy.
 * Timing comes from [TransitionPlan] (output timeline), so preview and export match exactly.
 *
 * Out-of-frame samples are mirrored (not clamped), which gives the seamless "infinite" look of
 * whip and zoom-out transitions without a second clip.
 */
@OptIn(UnstableApi::class)
class TransitionShaderEffect(private val windows: List<TransitionWindow>) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = Program(useHdr, windows)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = windows.isEmpty()

    private class Program(useHdr: Boolean, private val windows: List<TransitionWindow>) :
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
            set("uAspect", 1f)
            set("uType", 0f)
            set("uP", 0f)
        }

        private fun set(name: String, value: Float) = program.setFloatsUniformIfPresent(name, floatArrayOf(value))

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            set("uAspect", inputWidth.toFloat() / inputHeight.coerceAtLeast(1))
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val frame = TransitionPlan.at(windows, presentationTimeUs / 1000)
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                set("uType", frame?.let { TransitionPlan.shaderId(it.type).toFloat() } ?: 0f)
                set("uP", frame?.progress ?: 0f)
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

        /**
         * uP: -1..0 outgoing clip, 0..1 incoming clip (0 = the cut). e: 0 at the window edges,
         * 1 at the cut, eased. Every type is continuous at the window edges (e = 0 is the plain
         * frame), so a transition never pops in or out.
         */
        const val FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform float uType;
uniform float uP;
uniform float uAspect;
varying vec2 vTexSamplingCoord;

vec2 mirrorUv(vec2 uv) {
  vec2 m = mod(uv, 2.0);
  return mix(m, 2.0 - m, step(1.0, m));
}
vec4 tex(vec2 uv) { return texture2D(uTexSampler, mirrorUv(uv)); }
float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
vec2 rotate(vec2 uv, float a) {
  vec2 d = (uv - 0.5) * vec2(uAspect, 1.0);
  float c = cos(a);
  float s = sin(a);
  d = vec2(c * d.x - s * d.y, s * d.x + c * d.y);
  return d / vec2(uAspect, 1.0) + 0.5;
}

void main() {
  vec2 uv = vTexSamplingCoord;
  int t = int(uType + 0.5);
  if (t == 0) { gl_FragColor = texture2D(uTexSampler, uv); return; }
  float s = 1.0 - abs(uP);
  float e = s * s * (3.0 - 2.0 * s);
  if (e < 0.002) { gl_FragColor = texture2D(uTexSampler, uv); return; }
  float side = uP < 0.0 ? 1.0 : -1.0;   // +1 outgoing, -1 incoming
  float seed = floor((uP + 1.0) * 12.0);
  vec4 c = vec4(0.0);

  if (t == 1 || t == 2) {
    // Zoom in (push into the cut) / zoom out (mirrored tiles), with a radial blur.
    float z = t == 1 ? 1.0 + 0.9 * e : 1.0 / (1.0 + 1.5 * e);
    for (int i = 0; i < 8; i++) {
      float k = float(i) / 7.0;
      float zz = z * (1.0 + (t == 1 ? 0.14 : -0.1) * e * k);
      c += tex(0.5 + (uv - 0.5) / zz);
    }
    c /= 8.0;
  } else if (t >= 3 && t <= 6) {
    // Whip pan: the picture flies out one way and the next arrives from the other, motion-blurred.
    vec2 dir = t == 3 ? vec2(1.0, 0.0) : t == 4 ? vec2(-1.0, 0.0) : t == 5 ? vec2(0.0, -1.0) : vec2(0.0, 1.0);
    vec2 shift = dir * e * side;
    for (int i = 0; i < 10; i++) {
      float k = float(i) / 9.0 - 0.5;
      c += tex(uv + shift + dir * k * 0.35 * e);
    }
    c /= 10.0;
  } else if (t == 7) {
    // Spin: a half turn into the cut and out of it, with zoom and rotational blur.
    float a = side * 3.14159 * e * e;
    float z = 1.0 + 0.5 * e;
    for (int i = 0; i < 8; i++) {
      float k = float(i) / 7.0 - 0.5;
      c += tex(0.5 + (rotate(uv, a + k * 0.35 * e) - 0.5) / z);
    }
    c /= 8.0;
  } else if (t == 8) {
    // Blur: a soft defocus into the cut and back.
    float r = 0.035 * e;
    c = tex(uv) * 0.2;
    for (int i = 0; i < 8; i++) {
      float a = float(i) * 0.7854;
      vec2 o = vec2(cos(a) / uAspect, sin(a));
      c += tex(uv + o * r) * 0.06 + tex(uv + o * r * 0.5) * 0.04;
    }
  } else if (t == 9) {
    // Glitch: torn bands and RGB split.
    float band = floor(uv.y * 18.0);
    float h = hash(vec2(band, seed));
    float tear = step(0.55, h) * (hash(vec2(seed, band)) - 0.5) * 0.25 * e;
    vec2 q = uv + vec2(tear, 0.0);
    float split = 0.025 * e;
    c = vec4(tex(q + vec2(split, 0.0)).r, tex(q).g, tex(q - vec2(split, 0.0)).b, 1.0);
    c.rgb += step(0.97, hash(vec2(band * 3.1, seed))) * 0.25 * e;
  } else if (t == 10) {
    // Pixelate: blocks grow toward the cut.
    float n = mix(900.0, 14.0, e);
    vec2 cells = vec2(n * uAspect, n);
    c = tex((floor(uv * cells) + 0.5) / cells);
  } else if (t == 11) {
    // Iris: a circle closes to black on the cut and opens on the next clip.
    vec2 d = (uv - 0.5) * vec2(uAspect, 1.0);
    float r = (1.0 - e) * 0.5 * length(vec2(uAspect, 1.0));
    float mask = 1.0 - smoothstep(r - 0.01, r + 0.01, length(d));
    c = tex(uv) * mask;
  } else if (t == 12) {
    // Shake: a hard camera shake peaking on the cut.
    vec2 j = (vec2(hash(vec2(seed, 1.7)), hash(vec2(2.3, seed))) - 0.5) * 0.08 * e;
    c = tex(0.5 + (uv - 0.5) / (1.0 + 0.08 * e) + j);
  } else {
    // Film burn: a warm light sweep that floods the frame on the cut.
    vec4 base = tex(uv);
    float sweep = smoothstep(1.2 - 1.6 * e, 1.6 - 1.6 * e, uv.x * 0.7 + uv.y * 0.3 + 0.4);
    vec3 glow = vec3(1.0, 0.55, 0.22) * sweep + vec3(1.0, 0.9, 0.7) * e * e;
    c = vec4(1.0 - (1.0 - base.rgb) * (1.0 - clamp(glow * e, 0.0, 1.0)), 1.0);
  }
  gl_FragColor = vec4(c.rgb, 1.0);
}
"""
    }
}
