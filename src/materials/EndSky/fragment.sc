#ifndef INSTANCING
$input v_texcoord0, v_posTime
#endif

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/main.sh>

  SAMPLER2D_AUTOREG(s_SkyTexture);
#endif

// ========================================
// BLACKHOLE SKYBOX (End Dimension Only)
// Author: devendrn, License: CC BY-SA 4.0
// ========================================
#ifdef NL_BLACKHOLE
vec4 renderBlackhole(vec3 vdir, float t) {
    t *= NL_BH_SPEED;
    
    float r = 2.4;
    vec3 vr = vdir;
    // Rule 4: Use mul() for safe cross-platform matrix math
    vr.xy = mul(mat2(cos(r), -sin(r), sin(r), cos(r)), vr.xy);
    
    vec3 vd = vr - vec3(0.0, -1.0, 0.0);
    float nl = sin(15.0 * vd.x + t) * sin(15.0 * vd.y - t) * sin(15.0 * vd.z + t);
    float a = atan(vd.x, vd.z);
    
    float d = NL_BH_DIST * length(vd + 0.003 * nl);
    float d0 = (0.6 - d) / 0.6;
    float dm0 = 1.0 - max(d0, 0.0);
    
    float gl = 1.0 - clamp(-0.3 * d0, 0.0, 1.0);
    float gla = pow(1.0 - min(abs(d0), 1.0), 8.0);
    float gl8 = pow(gl, 8.0);
    
    float hole = 0.9 * pow(dm0, 32.0) + 0.1 * pow(dm0, 3.0);
    float bh = (gla + 0.8 * gl8 + 0.2 * gl8 * gl8) * hole;
    
    float df = sin(3.0 * a - 4.0 * d + 24.0 * pow(1.4 - d, 4.0) + t);
    df *= 0.9 + 0.1 * sin(8.0 * a + d + 4.0 * t - 4.0 * df);
    bh *= 1.0 + pow(df, 4.0) * hole * max(1.0 - bh, 0.0);
    
    vec3 col = bh * 4.0 * mix(NL_BH_COL_LOW, NL_BH_COL_HIGH, min(bh, 1.0));
    return vec4(col, hole);
}
#endif

void main() {
  #ifndef INSTANCING
    vec4 diffuse = texture2D(s_SkyTexture, v_texcoord0);

    // Extract view direction and time from v_posTime (Rule 2 compliant)
    vec3 viewDir = normalize(v_posTime.xyz);
    float t = v_posTime.w;

    // 1. Base End Sky (Newb-X default purple streaks)
    vec3 color = renderEndSky(getEndHorizonCol(), getEndZenithCol(), viewDir, t);
    
    // 2. Add vanilla End stars
    color += 2.8 * diffuse.rgb; 

    // 3. Blend Blackhole over the base End sky
    #ifdef NL_BLACKHOLE
        vec4 bh = renderBlackhole(viewDir, t);
        color *= bh.a;   // Absorb light in the center (the void)
        color += bh.rgb; // Add the glowing accretion ring
    #endif

    color = colorCorrection(color);

    gl_FragColor = vec4(color, 1.0);
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
