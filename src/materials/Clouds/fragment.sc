$input v_color0, v_color1, v_color2, v_dayFactor
#include <newb/config.h>

#include <bgfx_shader.sh>
#include <newb/main.sh>

uniform vec4 CameraPosition;

#define saturate(x) clamp(x, 0.0, 1.0)

// --- EXPERT 2.5D GRID CLOUD FUNCTIONS ---
highp float hash(highp vec2 p) {
    return fract(cos(p.x + p.y * 332.0) * 335.552);
}

vec2 smoothBlend(vec2 x) {
    vec2 v = x * x * (3.0 - 2.0 * x);
    return smoothstep(0.3, 0.95, v);
}

float gridSDF(vec2 uv) {
    vec2 f = fract(uv);
    vec2 i = floor(uv);

    float bl = step(0.85, hash(i + vec2(0.0, 0.0)));
    float br = step(0.85, hash(i + vec2(1.0, 0.0)));
    float tl = step(0.85, hash(i + vec2(0.0, 1.0)));
    float tr = step(0.85, hash(i + vec2(1.0, 1.0)));

    vec2 fade = smoothBlend(f);

    float mixBottom = mix(bl, br, fade.x);
    float mixTop    = mix(tl, tr, fade.x);
    float totalGrid = mix(mixBottom, mixTop, fade.y);

    return totalGrid;
}

vec3 pixelatedCloud(vec2 uv, float timeVal) {
    float a = 0.0;
    float b = 0.0;
    float isTime = -timeVal * 0.02;

    vec2 shadeDirection = vec2(0.05, 0.03);

    uv *= 6.5;
    uv.y *= 1.5;

    for (int i = 0; i < 4; i++) {
        uv /= 1.015;
        float r = gridSDF(uv + isTime);
        a = max(a, r);
    }

    vec2 shadeSampleUv = uv + isTime - shadeDirection;
    float shadeShape = gridSDF(shadeSampleUv);

    b = smoothstep(0.05, 0.9, shadeShape);
    a = smoothstep(0.1, 0.15, a);

    a -= b * a * 0.15;
    return vec3(saturate(a));
}
// ----------------------------------------

void main() {
  vec4 color = v_color0; 

  #if NL_CLOUD_TYPE == 2
    // Original Newb-X Rounded Clouds (Raymarching)
    vec3 vDir = normalize(v_color0.xyz);
    vec3 cloudPos = v_color0.xyz;
    cloudPos.xz += CameraPosition.xz;

    #define NL_CLOUD_PARAMS(x) NL_CLOUD2##x##STEPS, NL_CLOUD2##x##THICKNESS, NL_CLOUD2##x##RAIN_THICKNESS, NL_CLOUD2##x##VELOCITY, NL_CLOUD2##x##SCALE, NL_CLOUD2##x##DENSITY, NL_CLOUD2##x##SHAPE
    color = renderCloudsRounded(vDir, cloudPos, v_color1.w, v_color2.w, v_color2.rgb, v_color1.rgb, NL_CLOUD_PARAMS(_));

    #ifdef NL_CLOUD2_LAYER2
        vec2 parallax = vDir.xz / abs(vDir.y) * NL_CLOUD2_LAYER2_OFFSET;
        vec3 offsetPos = cloudPos;
        offsetPos.xz += parallax;
        vec4 color2 = renderCloudsRounded(vDir, offsetPos, v_color1.a, v_color2.a * 2.0, v_color2.rgb, v_color1.rgb, NL_CLOUD_PARAMS(_LAYER2_));
        color = mix(color2, color, 0.2 + 0.8 * color.a);
    #endif

    #ifdef NL_AURORA
        color += renderAuroraComplementary(vDir, v_color0.xz, v_color2.w, v_dayFactor) * (1.0 - 0.95 * color.a);
    #endif

    color.a *= v_color0.a;
    color.rgb = colorCorrection(color.rgb);

  #elif NL_CLOUD_TYPE == 3
    // Original Newb-X Realistic Clouds
    vec3 vDir3 = normalize(v_color0.xyz);
    vDir3.xz *= 0.3 + v_color0.w;
    vec2 p = (vDir3.xz) / (0.015 + 0.035 * abs(vDir3.y));
    p += 0.035 * CameraPosition.xz;
    vec4 clouds = renderClouds(p, v_color2.w, v_color1.w, v_color2.rgb, v_color1.rgb, NL_CLOUD3_SCALE, NL_CLOUD3_SPEED, NL_CLOUD3_SHADOW);
    color = clouds;

    #ifdef NL_AURORA
        p.xy *= 34.7;
        color += renderAuroraComplementary(vDir3, p, v_color2.w, v_dayFactor) * (1.0 - 0.95 * color.a);
    #endif

    color.a *= smoothstep(0.0, 0.7, vDir3.y);
    color.rgb = colorCorrection(color.rgb);

  #elif NL_CLOUD_TYPE == 4
    // === EXPERT 2.5D PIXELATED/GRID CLOUDS ===
    vec3 worldPos = v_color0.xyz; 
    float fadeFactor = v_color0.w; 
    float timeVal = v_color2.w;

    // Rule 5: World space conversion (NO gl_FragCoord!)
    vec2 cp = worldPos.xz * 0.005; 

    vec3 cloudNoise = pixelatedCloud(cp, timeVal);

    float cloudAlpha = clamp(length(cloudNoise.r), 0.0, 1.0);
    
    // Rule 6: Dynamic environment lighting
    vec3 baseHorizon = v_color2.rgb;
    vec3 cloudcol = baseHorizon / max(v_dayFactor, 0.01);
    cloudcol = clamp(cloudcol * 1.5, 0.0, 1.5);

    color = vec4(cloudcol, cloudAlpha * fadeFactor);
    
    #ifdef NL_AURORA
        vec3 vDir4 = normalize(v_color0.xyz);
        color.rgb += renderAuroraComplementary(vDir4, v_color0.xz, timeVal, v_dayFactor).rgb * (1.0 - color.a);
    #endif

    color.rgb = colorCorrection(color.rgb);
  #endif

  gl_FragColor = color;
}
