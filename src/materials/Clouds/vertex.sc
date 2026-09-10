$input a_color0, a_position
#ifdef INSTANCING
  $input i_data0, i_data1, i_data2, i_data3
#endif

$output v_color0, v_color1, v_color2, v_dayFactor

#include <newb/config.h>
#include <bgfx_shader.sh>
#include <newb/main.sh>

uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec4 TimeOfDay;
uniform vec4 CameraPosition;

float fog_fade(vec3 wPos) {
  return clamp(2.0 - length(wPos * vec3(0.005, 0.002, 0.005)), 0.0, 1.0);
}

void main() {
  #ifdef INSTANCING
    mat4 model = mtxFromCols(i_data0, i_data1, i_data2, i_data3);
  #else
    mat4 model = u_model[0];
  #endif

  float t = ViewPositionAndTime.w;
  float rain = detectRain(FogAndDistanceControl.xyz);

  nl_environment env;
  env.end = false;
  env.nether = false;
  env.underwater = false;
  env.rainFactor = rain;
  env = calculateSunParams(env, TimeOfDay.x);

  nl_skycolor skycol = nlOverworldSkyColors(env);
  vec3 pos = a_position;
  vec3 worldPos;

  #if NL_CLOUD_TYPE == 0
    pos.y *= (NL_CLOUD0_THICKNESS + rain*(NL_CLOUD0_RAIN_THICKNESS - NL_CLOUD0_THICKNESS));
    worldPos = mul(model, vec4(pos, 1.0)).xyz;

    v_color0.rgb = skycol.zenith + skycol.horizonEdge;
    v_color0.rgb += dot(v_color0.rgb, vec3(0.3,0.4,0.3))*a_position.y;
    v_color0.rgb *= 1.0 - 0.8*rain;
    v_color0.rgb = colorCorrection(v_color0.rgb);
    v_color0.a = NL_CLOUD0_OPACITY * fog_fade(worldPos.xyz);

    v_dayFactor = env.dayFactor;
    v_color1 = vec4(skycol.zenith, rain);
    v_color2 = vec4(skycol.horizonEdge, t);

    bool isL2 = a_color0.g > 0.5 * a_color0.b;
    if (isL2) {
      #ifdef NL_CLOUD0_MULTILAYER
        worldPos.y += 64.0;
      #else
        worldPos = vec3(0.0,0.0,0.0);
        v_color0.a = 0.0;
      #endif
    }
    gl_Position = mul(u_viewProj, vec4(worldPos, 1.0));

  #elif NL_CLOUD_TYPE == 1
    pos.y *= 0.01;
    worldPos.xyz = mul(model, vec4(pos, 1.0)).xyz;

    float fade = fog_fade(worldPos.xyz);
    float len = length(worldPos.xz)*0.01;
    worldPos.y -= len*len*clamp(0.2*worldPos.y, -1.0, 1.0);

    vec3 cloudPos = worldPos;
    cloudPos.xz += CameraPosition.xz;

    v_color0 = renderCloudsSimple(skycol, cloudPos, t, rain);
    worldPos.y -= NL_CLOUD1_DEPTH*v_color0.a*3.3;
    v_color0.a *= NL_CLOUD1_OPACITY;

    v_dayFactor = env.dayFactor;
    v_color1 = vec4(skycol.zenith, rain);
    v_color2 = vec4(skycol.horizonEdge, t);

    v_color0.a *= fade;
    v_color0.rgb = colorCorrection(v_color0.rgb);
    gl_Position = mul(u_viewProj, vec4(worldPos, 1.0));

  #elif NL_CLOUD_TYPE == 2 || NL_CLOUD_TYPE == 4
    // CRITICAL FIX: Type 4 now gets the same world position data as Type 2!
    pos.y *= 0.01; 
    worldPos.xyz = mul(model, vec4(pos, 1.0)).xyz;
    float fade = fog_fade(worldPos.xyz);

    v_dayFactor = env.dayFactor;
    v_color1 = vec4(skycol.zenith, rain);
    v_color2 = vec4(skycol.horizonEdge, t);
    
    v_color0 = vec4(worldPos, fade);
    gl_Position = mul(u_viewProj, vec4(worldPos, 1.0));

  #else
    v_dayFactor = env.dayFactor;
    v_color0 = vec4(0.0, 0.0, 0.0, 0.0);
    v_color1 = vec4(skycol.zenith, rain);
    v_color2 = vec4(skycol.horizonEdge, t);
    gl_Position = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
