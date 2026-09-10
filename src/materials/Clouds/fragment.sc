$input v_color0

#include <newb/config.h>

#if NL_CLOUD_TYPE >= 2
$input v_color1, v_color2, v_dayFactor
#endif

#include <bgfx_shader.sh>
#include <newb/main.sh>

uniform vec4 CameraPosition;

#define NL_CLOUD_PARAMS(x) NL_CLOUD2##x##STEPS, NL_CLOUD2##x##THICKNESS, NL_CLOUD2##x##RAIN_THICKNESS, NL_CLOUD2##x##VELOCITY, NL_CLOUD2##x##SCALE, NL_CLOUD2##x##DENSITY, NL_CLOUD2##x##SHAPE

void main() {
    vec4 color = v_color0;

#if NL_CLOUD_TYPE >= 2
    vec3 vDir = normalize(v_color0.xyz);
    vec3 cloudPos = v_color0.xyz;
    cloudPos.xz += CameraPosition.xz;

    #if NL_CLOUD_TYPE == 2
        // Rounded Clouds (Raymarching)
        color = renderCloudsRounded(vDir, cloudPos, v_color1.w, v_color2.w, v_color2.rgb, v_color1.rgb, NL_CLOUD_PARAMS(_));

        #ifdef NL_CLOUD2_LAYER2
            vec2 parallax = vDir.xz / abs(vDir.y) * NL_CLOUD2_LAYER2_OFFSET;
            vec3 offsetPos = cloudPos;
            offsetPos.xz += parallax;
            vec4 color2 = renderCloudsRounded(vDir, offsetPos, v_color1.a, v_color2.a * 2.0, v_color2.rgb, v_color1.rgb, NL_CLOUD_PARAMS(_LAYER2_));
            color = mix(color2, color, 0.2 + 0.8 * color.a);
        #endif

        #ifdef NL_AURORA
            color += renderAurora(cloudPos, v_color2.a, v_color1.a, v_dayFactor) * (1.0 - 0.95 * color.a);
        #endif

        color.a *= v_color0.a;

    #elif NL_CLOUD_TYPE == 3
        // Realistic Clouds (Voronoi-based)
        vDir.xz *= 0.3 + v_color0.w; // height parallax
        vec2 p = (vDir.xz) / (0.015 + 0.035 * abs(vDir.y));
        p += 0.035 * CameraPosition.xz;
        vec4 clouds = renderClouds(p, v_color2.w, v_color1.w, v_color2.rgb, v_color1.rgb, NL_CLOUD3_SCALE, NL_CLOUD3_SPEED, NL_CLOUD3_SHADOW);
        color = clouds;

        #ifdef NL_AURORA
            p.xy *= 34.7;
            color += renderAurora(vec3(p.xyy), v_color2.w, v_color1.w, v_dayFactor) * (1.0 - 0.95 * color.a);
        #endif

        color.a *= smoothstep(0.0, 0.7, vDir.y);

    #elif NL_CLOUD_TYPE == 4
        // --- KRISPY 2.5D LIGHTWEIGHT CLOUDS ---
        // Spherical dome projection - no raymarching, ultra fast for mobile
        float perspective = 0.8 / max(vDir.y, 0.001);
        vec2 uv = vDir.xz * perspective;

        // Smooth camera movement so clouds drift naturally with the player
        uv += CameraPosition.xz * 0.001;

        // Sample the lightweight 2D Voronoi cloud function
        vec4 clouds = renderClouds(uv, v_color2.w, v_color1.w, v_color2.rgb, v_color1.rgb, NL_CLOUD4_SCALE, NL_CLOUD4_SPEED, NL_CLOUD4_SHADOW);
        color = clouds;

        // Fade out near the horizon for a clean blend with the sky
        color.a *= smoothstep(0.0, 0.4, vDir.y);

        #ifdef NL_AURORA
            // Aurora scaled to match the 2.5D projection
            vec2 auroraUV = uv * 34.7;
            color += renderAurora(vec3(auroraUV, vDir.y), v_color2.w, v_color1.w, v_dayFactor) * (1.0 - 0.95 * color.a);
        #endif
    #endif

    color.rgb = colorCorrection(color.rgb);
#endif

    gl_FragColor = color;
}
