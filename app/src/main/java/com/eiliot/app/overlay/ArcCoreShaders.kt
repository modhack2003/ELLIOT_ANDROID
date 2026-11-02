package com.eiliot.app.overlay

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Factory + source for the Arc Core master AGSL shader.
 *
 * One fragment shader renders every orb state procedurally (energy core,
 * segmented arc ring, HUD ticks, particles, waveform bars, reticle, ripples,
 * radar sweep, constellation) and *crossfades between states on the GPU* by
 * evaluating the previous and current mode and mixing by `uMix`.
 *
 * AGSL `RuntimeShader` requires API 33 (Android 13). minSdk is 26, so every
 * reference to [RuntimeShader] is isolated behind SDK guards; older devices
 * are served by [ArcCoreCanvasRenderer] instead. The shader source lives here
 * as a string constant (not res/raw) so uniform names and the renderer stay in
 * one reviewed file.
 *
 * Uniform contract (must match [ArcCoreView.pushUniforms]):
 *   uSize    float2 — view size in px
 *   uTime    float  — seconds, monotonic while attached
 *   uAmp     float  — smoothed audio amplitude 0..1
 *   uMode    int    — current OrbState.ordinal
 *   uPrevMode int   — previous OrbState.ordinal (crossfade source)
 *   uMix     float  — 0 = fully previous, 1 = fully current
 */
internal object ArcCoreShaders {

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Compiles the master shader, or returns null when AGSL is unavailable or
     * compilation fails (caller then uses the Canvas fallback). Never throws.
     */
    fun createMasterShader(): Shader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            create33()
        } catch (t: Throwable) {
            // Compile logs ride in the exception message on most devices.
            Log.w("ArcCore", "AGSL master shader failed; Canvas fallback active", t)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun create33(): Shader = RuntimeShader(MASTER_AGSL)

    private const val MASTER_AGSL = """
const float TAU = 6.2831853;

uniform float2 uSize;
uniform float  uTime;
uniform float  uAmp;
uniform int    uMode;
uniform int    uPrevMode;
uniform float  uMix;

// ---------------------------------------------------------------- utilities

float hash21(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * vnoise(p);
        p = p * 2.03 + float2(17.3, 9.1);
        amp *= 0.5;
    }
    return v;
}

// Soft radial band of half-width w centred on c.
float band(float r, float c, float w) {
    return 1.0 - smoothstep(w, w * 2.0, abs(r - c));
}

// State palettes — must mirror OrbState.kt.
float3 palette(int m) {
    if (m == 0) return float3(0.000, 0.898, 1.000); // IDLE       #00E5FF
    if (m == 1) return float3(0.557, 0.965, 1.000); // LISTENING  #8EF6FF
    if (m == 2) return float3(0.486, 0.549, 1.000); // THINKING   #7C8CFF
    if (m == 3) return float3(0.243, 0.949, 0.847); // SPEAKING   #3EF2D8
    if (m == 4) return float3(1.000, 0.353, 0.165); // ALERT      #FF5A2A
    return float3(0.655, 0.545, 1.000);             // LEARNING   #A78BFF
}

// ------------------------------------------------------------- mode layers

// Orbiting particle field. IDLE swirls, THINKING spirals inward, LEARNING is
// silent here (its nodes are drawn by constellation()).
float3 particles(float2 uv, int m, float t, float3 base) {
    if (m == 5) return float3(0.0, 0.0, 0.0);
    float3 acc = float3(0.0, 0.0, 0.0);
    for (int i = 0; i < 24; i++) {
        float fi = float(i);
        float h1 = hash21(float2(fi, 1.7));
        float h2 = hash21(float2(fi, 9.2));
        float h3 = hash21(float2(fi, 4.1));
        float dir = h2 > 0.5 ? 1.0 : -1.0;
        float ang = h1 * TAU + t * (0.15 + 0.45 * h2) * dir;
        float rad = 0.40 + 0.42 * h3 + 0.04 * sin(t * (0.4 + h2) + fi);
        if (m == 2) {
            float cyc = fract(h3 - t * (0.10 + 0.28 * h2));
            rad = 0.08 + 0.82 * cyc;
            ang += (1.0 - cyc) * 4.0; // tighten the spiral as it ingests
        }
        float2 pos = float2(cos(ang), sin(ang)) * rad;
        float d = length(uv - pos);
        acc += base * (0.00045 / (d * d + 0.00035)) * 0.5;
    }
    return acc;
}

// LISTENING — waveform bars orbiting just outside the main ring.
float3 waveform(float an, float r, float R, float t, float amp, float3 base) {
    float fb = fract(an * 40.0);
    float barMask = smoothstep(0.05, 0.22, fb) * smoothstep(0.95, 0.78, fb);
    float bin = floor(an * 40.0);
    float tt = t * 9.0;
    float ft = fract(tt);
    ft = ft * ft * (3.0 - 2.0 * ft);
    float n = mix(hash21(float2(bin, floor(tt))),
                  hash21(float2(bin, floor(tt) + 1.0)), ft);
    float len = (0.05 + 0.30 * n) * (0.35 + 0.75 * amp);
    float inner = R + 0.045;
    float radial = smoothstep(inner, inner + 0.01, r)
                 * (1.0 - smoothstep(inner + len - 0.01, inner + len, r));
    float3 wc = mix(base, float3(1.0, 1.0, 1.0), 0.65);
    return wc * barMask * radial * 0.9;
}

// THINKING — spinning reticle: crosshair quadrant spokes + notched ring.
float3 reticle(float2 uv, float t, float3 base) {
    float ca = cos(t * 0.9);
    float sa = sin(t * 0.9);
    float2 q = float2(uv.x * ca - uv.y * sa, uv.x * sa + uv.y * ca);
    float crossI = (1.0 - smoothstep(0.002, 0.007, abs(q.x)))
                 + (1.0 - smoothstep(0.002, 0.007, abs(q.y)));
    float win = smoothstep(0.30, 0.34, length(uv))
              * (1.0 - smoothstep(0.48, 0.52, length(uv)));
    float an2 = atan(q.y, q.x) / TAU + 0.5;
    float notch = step(0.7, fract(an2 * 4.0));
    float ringB = band(length(uv), 0.42, 0.010) * (0.3 + 0.7 * notch);
    return base * (crossI * win * 0.5 + ringB * 0.9);
}

// SPEAKING — expanding ripple rings, amplitude-driven.
float3 ripples(float r, float t, float amp, float3 base) {
    float3 acc = float3(0.0, 0.0, 0.0);
    for (int k = 0; k < 3; k++) {
        float ph = fract(t * 0.55 + float(k) * 0.3333);
        float rad = 0.30 + 0.66 * ph;
        float w = 0.012 + 0.02 * amp;
        acc += base * band(r, rad, w)
             * pow(1.0 - ph, 1.6) * (0.25 + 0.85 * amp);
    }
    return acc;
}

// ALERT — radar sweep with phosphor trail + expanding pulse ring.
float3 radar(float an, float r, float t, float3 base) {
    float region = smoothstep(0.30, 0.36, r) * (1.0 - smoothstep(0.80, 0.86, r));
    float d = fract(fract(t * 0.35) - an + 10.0);
    float trail = exp(-d * 5.0);
    float3 acc = base * region * trail * 1.4;
    float ph = fract(t * 0.8);
    float rad = 0.15 + 0.8 * ph;
    acc += base * band(r, rad, 0.02) * pow(1.0 - ph, 1.4) * 0.9;
    return acc;
}

// LEARNING — drifting constellation nodes + proximity links.
float2 nodePos(int i, float t) {
    float fi = float(i);
    float h1 = hash21(float2(fi, 3.3));
    float h2 = hash21(float2(fi, 7.7));
    float h3 = hash21(float2(fi, 5.1));
    float x = 0.55 * sin(t * (0.10 + 0.12 * h1) + h2 * TAU);
    float y = 0.55 * cos(t * (0.08 + 0.14 * h3) + h1 * TAU);
    return float2(x, y);
}

float segDist(float2 p, float2 a, float2 b) {
    float2 ab = b - a;
    float tt = clamp(dot(p - a, ab) / max(dot(ab, ab), 0.00001), 0.0, 1.0);
    return length(p - (a + ab * tt));
}

float3 constellation(float2 uv, float t, float3 base) {
    float3 acc = float3(0.0, 0.0, 0.0);
    for (int i = 0; i < 8; i++) {
        float2 ni = nodePos(i, t);
        float dn = length(uv - ni);
        acc += base * (0.0008 / (dn * dn + 0.0005)) * 0.6;
        for (int j = 0; j < 8; j++) {
            if (j > i) {
                float2 nj = nodePos(j, t);
                float len = length(ni - nj);
                if (len < 0.62) {
                    float sd = segDist(uv, ni, nj);
                    float li = (1.0 - smoothstep(0.002, 0.006, sd))
                             * (1.0 - len / 0.62);
                    acc += base * li * 0.30;
                }
            }
        }
    }
    return acc;
}

// ------------------------------------------------------------------- scene

float3 scene(float2 uv, int m) {
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float an = a / TAU + 0.5;
    float t = uTime;
    float3 base = palette(m);

    float bSpeed = m == 4 ? 1.6 : (m == 3 ? 0.9 : 0.35);
    float breathe = 0.5 + 0.5 * sin(t * bSpeed * TAU * 0.25);

    // Energy core — fbm-shimmered radial falloff, hot white centre.
    float coreR = 0.30 + 0.035 * breathe;
    if (m == 3) coreR += uAmp * 0.10;
    if (m == 1) coreR *= 0.92;
    float core = exp(-r * r / max(coreR * coreR, 0.0001) * 3.2);
    float n = fbm(uv * 3.5 + float2(0.0, -t * 0.5) + float2(t * 0.11, 0.0));
    core *= 0.72 + 0.55 * n;
    float3 col = mix(base, float3(1.0, 1.0, 1.0), core * 0.9)
               * core * (m == 4 ? 1.25 : 1.0);

    // Main segmented arc ring (arc-reactor DNA).
    float R = 0.62 + 0.02 * breathe;
    if (m == 1) R += 0.06 + 0.03 * uAmp;      // listening dilates
    if (m == 2) R = 0.56;
    float dashN = m == 0 ? 3.0 : (m == 4 ? 6.0 : 4.0);
    float dashSpeed = m == 4 ? 0.5 : (m == 2 ? -0.3 : 0.12);
    float ringB = band(r, R, m == 1 ? 0.030 : 0.022);
    float dash = step(0.38, fract(an * dashN + t * dashSpeed));
    col += mix(base, float3(1.0, 1.0, 1.0), 0.25)
         * ringB * (0.30 + 0.70 * dash) * (m == 4 ? 1.3 : 1.0);

    // Outer fine HUD ticks, counter-drifting.
    float ticks = step(0.55, fract(an * 60.0 - t * (m == 2 ? 0.10 : 0.03)));
    col += base * band(r, 0.80, 0.008) * ticks * (m == 1 ? 0.6 : 0.35);

    // Mode-specific layers.
    if (m == 1) col += waveform(an, r, R, t, uAmp, base);
    if (m == 2) col += reticle(uv, t, base);
    if (m == 3) col += ripples(r, t, uAmp, base);
    if (m == 4) col += radar(an, r, t, base);
    if (m == 5) col += constellation(uv, t, base);
    col += particles(uv, m, t, base);

    // Alert heartbeat — the whole orb throbs.
    if (m == 4) col *= 0.9 + 0.35 * (0.5 + 0.5 * sin(t * 9.0));

    // Vignette so nothing clips the view bounds.
    col *= 1.0 - smoothstep(0.94, 1.0, r);
    return col;
}

half4 main(float2 frag) {
    float2 uv = (frag - uSize * 0.5) / (0.5 * min(uSize.x, uSize.y));
    float3 col;
    if (uMix > 0.999) {
        col = scene(uv, uMode);
    } else {
        col = mix(scene(uv, uPrevMode), scene(uv, uMode),
                  smoothstep(0.0, 1.0, uMix));
    }
    float luma = max(col.r, max(col.g, col.b));
    float alpha = clamp(luma * 1.25, 0.0, 1.0);
    // Faint dark glass disc behind the orb for contrast on light screens.
    alpha = max(alpha, (1.0 - smoothstep(0.75, 0.95, length(uv))) * 0.35);
    return half4(clamp(col, 0.0, 1.0), alpha);
}
"""
}
