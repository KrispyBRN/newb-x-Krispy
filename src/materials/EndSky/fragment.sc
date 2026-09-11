#ifndef INSTANCING
$input v_texcoord0, v_posTime
#endif

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/main.sh>

  SAMPLER2D_AUTOREG(s_SkyTexture);
#endif

//blackhole

#ifdef NL_BLACKHOLE
vec4 renderBlackhole(vec3 vdir, float t) {
    t *= NL_BH_SPEED;
    
    float r = 2.4;
    vec3 vr = vdir;
    
    // FIXED: Safe manual 2D rotation for cross-platform bgfx compatibility
    float cr = cos(r);
    float sr = sin(r);
    vr.xy = vec2(vr.x * cr - vr.y * sr, vr.x * sr + vr.y * cr);
    
    // Offset the black hole position up into the sky so it's visible
    vec3 vd = vr - vec3(0.0, 2.0, 0.0);
    float nl = sin(15.0 * vd.x + t) * sin(15.0 * vd.y - t) * sin(15.0 * vd.z + t);
    float a = atan2(vd.z, vd.x); // Fixed: Standard atan2 layout for stable angle tracking
    
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

    // Extract the raw layout values from the vertex shader packet
    // v_posTime.xyz is the rotated world position of the skybox vertex
    vec3 viewDir = normalize(v_posTime.xyz);
    float t = v_posTime.w;
    
     // Build the default Newb base sky for The End
    vec3 color = renderEndSky(getEndHorizonCol(), getEndZenithCol(), viewDir, t);
    
    // Add the native cloud texture overlays over the horizon
    color += 2.8 * diffuse.rgb; 

    // Blend Blackhole mathematical layer over the base sky box
    #ifdef NL_BLACKHOLE
        vec4 bh = renderBlackhole(viewDir, t);
        color *= bh.a;   // Absorb light in the center (forces black singularity)
        color += bh.rgb; // Add the burning outer accretion disk
    #endif

    // Run final color mapping and tone correction configuration
    color = colorCorrection(color);

    gl_FragColor = vec4(color, 1.0);
  #else
    // Instanced geometry drops draw buffer execution
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
