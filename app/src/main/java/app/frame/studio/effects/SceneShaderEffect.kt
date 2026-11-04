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
import app.frame.studio.domain.SceneEffect
import app.frame.studio.domain.SceneEffectType

/**
 * A procedurally drawn scene effect (rain, snow, confetti, glitch…) applied to the composed frame
 * between [SceneEffect.startMs] and [SceneEffect.endMs]. Everything is generated in the fragment
 * shader from hashes and time, so effects need no image or video assets.
 *
 * Applied as a composition effect, so presentation timestamps are on the output timeline.
 */
@OptIn(UnstableApi::class)
class SceneShaderEffect(private val effect: SceneEffect) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram = Program(useHdr, effect)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = effect.intensity <= 0f || effect.endMs <= effect.startMs

    private class Program(useHdr: Boolean, private val effect: SceneEffect) :
        BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

        private val program: GlProgram = try {
            GlProgram(VERTEX, HEADER + body(effect.type))
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
            setIfPresent("uAspect", 1f)
            setIfPresent("uTime", 0f)
            setIfPresent("uAmount", 0f)
        }

        /** Shaders that don't use a uniform have it optimised away; setting it would throw. */
        private fun setIfPresent(name: String, value: Float) {
            program.setFloatsUniformIfPresent(name, floatArrayOf(value))
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            setIfPresent("uAspect", inputWidth.toFloat() / inputHeight.coerceAtLeast(1))
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val tMs = presentationTimeUs / 1000
                val amount = SceneEffects.envelope(effect, tMs)
                // Seconds since the effect started; wrapped to keep float precision in the shader.
                val seconds = ((tMs - effect.startMs).coerceAtLeast(0) % 600_000L) / 1000f
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                setIfPresent("uTime", seconds)
                setIfPresent("uAmount", amount)
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

        /** Shared uniforms and helpers. uv: 0..1 with y up; p: aspect-corrected position. */
        private const val HEADER = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform float uTime;
uniform float uAmount;
uniform float uAspect;
varying vec2 vTexSamplingCoord;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

vec3 screen(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - b); }
"""

        private fun body(type: SceneEffectType): String = when (type) {
            SceneEffectType.RAIN -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec2 p = vec2(uv.x * uAspect, uv.y);
  float rain = 0.0;
  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    float scale = 28.0 + fi * 22.0;
    vec2 q = p * vec2(scale, scale * 0.12);
    q.y += uTime * (7.0 + fi * 3.0);
    q.x += q.y * 0.09;
    vec2 id = floor(q);
    vec2 f = fract(q);
    float h = hash(id + fi * 13.1);
    float drop = step(0.8, h) * (1.0 - smoothstep(0.0, 0.07, abs(f.x - 0.5))) * smoothstep(0.0, 0.35, f.y) * (1.0 - smoothstep(0.55, 1.0, f.y));
    rain += drop * (0.7 - fi * 0.18);
  }
  c = mix(c, c * 0.82 + vec3(0.02, 0.03, 0.05), uAmount * 0.6);
  c += vec3(0.78, 0.84, 0.95) * clamp(rain, 0.0, 1.0) * uAmount;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
            SceneEffectType.SNOW -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec2 p = vec2(uv.x * uAspect, uv.y);
  float snow = 0.0;
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    float scale = 5.0 + fi * 4.5;
    vec2 q = p * scale;
    q.y += uTime * (0.5 + fi * 0.3);
    q.x += sin(uTime * 0.7 + fi * 1.7 + q.y * 0.6) * 0.3;
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    vec2 o = (vec2(hash(id + 3.1), hash(id + 9.7)) - 0.5) * 0.6;
    float r = 0.04 + 0.07 * hash(id + 1.3);
    float flake = step(0.5, hash(id + fi * 7.1)) * (1.0 - smoothstep(r * 0.25, r, length(f - o)));
    snow += flake * (1.0 - fi * 0.15);
  }
  c = mix(c, c * 0.9 + vec3(0.07, 0.08, 0.1), uAmount * 0.45);
  c += vec3(1.0) * clamp(snow, 0.0, 1.0) * uAmount;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
            SceneEffectType.FOG -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec2 p = vec2(uv.x * uAspect, uv.y) * 3.0;
  float n = 0.0;
  float amp = 0.5;
  for (int i = 0; i < 4; i++) {
    n += noise(p + vec2(uTime * (0.08 + float(i) * 0.04), uTime * 0.02)) * amp;
    p *= 2.03;
    amp *= 0.5;
  }
  float fog = smoothstep(0.25, 0.85, n) * (0.55 + 0.45 * (1.0 - uv.y));
  c = mix(c, vec3(0.86, 0.88, 0.92), fog * 0.7 * uAmount);
  gl_FragColor = vec4(c, 1.0);
}
"""
            SceneEffectType.CONFETTI -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec2 p = vec2(uv.x * uAspect, uv.y);
  vec3 col = vec3(0.0);
  float mask = 0.0;
  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    float scale = 8.0 + fi * 5.0;
    vec2 q = p * scale;
    q.y += uTime * (1.1 + fi * 0.45);
    q.x += sin(uTime * 0.9 + fi * 2.1 + q.y * 0.4) * 0.25;
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    float h = hash(id + fi * 5.3);
    float a = uTime * (2.0 + h * 5.0) + h * 6.28;
    vec2 rf = vec2(cos(a) * f.x - sin(a) * f.y, sin(a) * f.x + cos(a) * f.y);
    float flip = 0.25 + 0.75 * abs(sin(a * 1.3));
    float piece = step(0.62, h) * step(abs(rf.x), 0.13) * step(abs(rf.y), 0.06 * flip);
    vec3 pc = 0.55 + 0.45 * cos(6.2832 * (h * 3.0 + vec3(0.0, 0.33, 0.67)));
    col = mix(col, pc, piece);
    mask = max(mask, piece);
  }
  c = mix(c, col, mask * uAmount);
  gl_FragColor = vec4(c, 1.0);
}
"""
            SceneEffectType.SPARKLE -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec2 p = vec2(uv.x * uAspect, uv.y);
  float s = 0.0;
  for (int i = 0; i < 2; i++) {
    float fi = float(i);
    vec2 q = p * (16.0 + fi * 12.0);
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    float h = hash(id + fi * 3.3);
    float tw = sin(uTime * (2.5 + h * 4.0) + h * 30.0) * 0.5 + 0.5;
    float star = (1.0 - smoothstep(0.0, 0.06, abs(f.x))) * (1.0 - smoothstep(0.0, 0.38, abs(f.y)))
               + (1.0 - smoothstep(0.0, 0.06, abs(f.y))) * (1.0 - smoothstep(0.0, 0.38, abs(f.x)))
               + (1.0 - smoothstep(0.0, 0.12, length(f)));
    s += step(0.86, h) * star * tw * tw;
  }
  c = screen(c, vec3(1.0, 0.95, 0.8) * clamp(s, 0.0, 1.0) * uAmount);
  gl_FragColor = vec4(c, 1.0);
}
"""
            SceneEffectType.HEARTS -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec2 p = vec2(uv.x * uAspect, uv.y);
  float m = 0.0;
  for (int i = 0; i < 2; i++) {
    float fi = float(i);
    vec2 q = p * (4.5 + fi * 3.0);
    q.y -= uTime * (0.5 + fi * 0.25);
    q.x += sin(uTime * 1.2 + q.y + fi) * 0.15;
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    float h = hash(id + fi * 11.0);
    vec2 o = (vec2(hash(id + 2.0), hash(id + 6.0)) - 0.5) * 0.4;
    vec2 hp = (f - o) / (0.16 + 0.08 * hash(id + 4.0));
    hp.y += 0.25;
    float x2 = hp.x * hp.x;
    float k = x2 + hp.y * hp.y - 1.0;
    float v = k * k * k - x2 * hp.y * hp.y * hp.y;
    m = max(m, step(0.6, h) * (1.0 - smoothstep(-0.02, 0.02, v)) * (0.75 - fi * 0.2));
  }
  c = mix(c, vec3(1.0, 0.36, 0.55), m * uAmount);
  gl_FragColor = vec4(c, 1.0);
}
"""
            SceneEffectType.BOKEH -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec2 p = vec2(uv.x * uAspect, uv.y);
  vec3 b = vec3(0.0);
  for (int i = 0; i < 3; i++) {
    float fi = float(i);
    vec2 q = p * (2.2 + fi * 1.4);
    q.y -= uTime * (0.05 + fi * 0.03);
    q.x += uTime * 0.02;
    vec2 id = floor(q);
    vec2 f = fract(q) - 0.5;
    float h = hash(id + fi);
    vec2 o = (vec2(hash(id + 2.0), hash(id + 5.0)) - 0.5) * 0.5;
    float r = 0.16 + 0.12 * hash(id + 7.0);
    float disk = (1.0 - smoothstep(r * 0.7, r, length(f - o))) * step(0.45, h);
    vec3 col = mix(vec3(1.0, 0.78, 0.45), vec3(1.0, 0.55, 0.75), hash(id + 11.0));
    b += col * disk * 0.32 * (0.6 + 0.4 * sin(uTime * 0.8 + h * 6.28));
  }
  c = screen(c, clamp(b, 0.0, 1.0) * uAmount);
  gl_FragColor = vec4(c, 1.0);
}
"""
            SceneEffectType.LIGHT_LEAK -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec2 p = vec2(uv.x * uAspect, uv.y);
  vec2 a = vec2((0.1 + 0.25 * sin(uTime * 0.35)) * uAspect, 0.85 + 0.12 * cos(uTime * 0.27));
  vec2 b = vec2((0.95 + 0.2 * cos(uTime * 0.22)) * uAspect, 0.2 + 0.2 * sin(uTime * 0.31));
  float g1 = exp(-dot(p - a, p - a) * 3.0);
  float g2 = exp(-dot(p - b, p - b) * 4.0);
  vec3 leak = vec3(1.0, 0.45, 0.15) * g1 + vec3(1.0, 0.8, 0.35) * g2 * 0.8;
  c = screen(c, clamp(leak, 0.0, 1.0) * uAmount * 0.85);
  gl_FragColor = vec4(c, 1.0);
}
"""
            SceneEffectType.OLD_FILM -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  float frame = floor(uTime * 24.0);
  float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
  vec3 sepia = vec3(l * 1.07, l * 0.94, l * 0.78);
  c = mix(c, sepia, 0.6 * uAmount);
  c *= 1.0 + (hash(vec2(frame, 3.0)) - 0.5) * 0.14 * uAmount;
  float sx = hash(vec2(floor(uTime * 6.0), 5.0));
  float scratch = (1.0 - smoothstep(0.0, 0.0025, abs(uv.x - sx))) * step(0.45, hash(vec2(floor(uTime * 6.0), 7.0)));
  c = mix(c, vec3(0.92, 0.9, 0.85), scratch * 0.55 * uAmount);
  float dust = step(0.9985, hash(floor(uv * vec2(480.0, 270.0)) + frame));
  c = mix(c, vec3(0.08), dust * uAmount);
  vec2 d = uv - 0.5;
  c *= 1.0 - dot(d, d) * 0.9 * uAmount;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
            SceneEffectType.GLITCH -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  float t = floor(uTime * 12.0);
  float burst = step(0.7, hash(vec2(t, 1.0)));
  float slice = floor(uv.y * 22.0);
  float off = (hash(vec2(slice, t)) - 0.5) * 0.08 * burst * uAmount;
  vec2 u2 = vec2(uv.x + off, uv.y);
  float split = 0.007 * uAmount * (0.4 + burst);
  vec3 c;
  c.r = texture2D(uTexSampler, u2 + vec2(split, 0.0)).r;
  c.g = texture2D(uTexSampler, u2).g;
  c.b = texture2D(uTexSampler, u2 - vec2(split, 0.0)).b;
  c += (hash(uv * 400.0 + t) - 0.5) * 0.08 * burst * uAmount;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
            SceneEffectType.SHAKE -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  float t = floor(uTime * 20.0);
  vec2 o = (vec2(hash(vec2(t, 1.0)), hash(vec2(t, 2.0))) - 0.5) * 0.025 * uAmount;
  float z = 1.0 + 0.05 * uAmount;
  vec2 u2 = (uv - 0.5) / z + 0.5 + o;
  gl_FragColor = vec4(texture2D(uTexSampler, u2).rgb, 1.0);
}
"""
            SceneEffectType.PULSE -> """
void main() {
  vec2 uv = vTexSamplingCoord;
  float beat = exp(-fract(uTime * 2.0) * 6.0);
  float z = 1.0 + 0.07 * uAmount * beat;
  vec2 u2 = (uv - 0.5) / z + 0.5;
  vec3 c = texture2D(uTexSampler, u2).rgb;
  c *= 1.0 + 0.12 * beat * uAmount;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
        }
    }
}
