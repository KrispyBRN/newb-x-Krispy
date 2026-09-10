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
        // Original rounded clouds (raymarching)
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
        // Realistic clouds
        vDir.xz *= 0.3 + v_color0.w;
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
        // === KRISPY 2.5D ROUNDED CLOUDS ===
        // Uses the exact same cloudDf function as Type 2, but sampled on a 2.5D dome.
        // This gives beautiful 3D-looking clouds without the heavy raymarching performance cost!
        
        // 1. 2.5D Dome Projection
        float perspective = 0.8 / max(vDir.y, 0.001);
        vec2 uv = vDir.xz * perspective;
        
        // 2. Time and rain factors
        float time = v_color2.w;
        float rain = v_color1.w;
        
        // 3. Sample position on the dome (fixed height to avoid raymarching loop)
        // FIXED: Proper vec2 division to prevent compilation error
        vec3 samplePos;
        samplePos.xz = (uv + CameraPosition.xz * 0.01) / NL_CLOUD4_SCALE; 
        samplePos.y = 0.5; 
        
        // 4. Use the exact same cloudDf function as rounded clouds!
        float density = cloudDf(samplePos, rain, NL_CLOUD4_SHAPE);
        
        // 5. Add secondary layer for depth (shadow offset)
        vec3 samplePos2 = samplePos;
        samplePos2.xz += NL_CLOUD4_SHADOW_OFFSET * 50.0;
        float density2 = cloudDf(samplePos2, rain, NL_CLOUD4_SHAPE) * 0.5;
        
        // 6. Combine layers and smooth
        float cloudAlpha = density + density2;
        cloudAlpha = smoothstep(0.15, 0.85, cloudAlpha);
        
        // 7. Fade at horizon for a clean blend
        cloudAlpha *= smoothstep(0.0, 0.4, vDir.y);
        
        // 8. Cloud colors and lighting
        vec3 horizonCol = v_color2.rgb;
        vec3 zenithCol = v_color1.rgb;
        vec4 clouds = vec4(zenithCol + horizonCol, cloudAlpha);
        
        float lighting = 1.0 - samplePos.y * 0.5;
        clouds.rgb *= lighting;
        clouds.rgb *= 1.0 - 0.8 * rain;
        
        color = clouds;
        color.a *= v_color0.a;

        #ifdef NL_AURORA
            // Keep original aurora working in the background
            vec2 auroraUV = vDir.xz * 1.5;
            color.rgb += renderAurora(vec3(auroraUV, vDir.y), time, rain, v_dayFactor).rgb * (1.0 - color.a);
        #endif
    #endif

    color.rgb = colorCorrection(color.rgb);
#endif

    gl_FragColor = color;
}
