#version 330

#moj_import <minecraft:globals.glsl>

// Lumen screen pass: sunrise/sunset, haze, ground mist, storm fog & rain, heat shimmer,
// underwater rays & depth, then bloom, warm grade and vignette. All values come live from DataSampler.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D BloomSampler;
uniform sampler2D DataSampler;
uniform sampler2D LightSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

const float NEAR = 0.05;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec4 dataAt(int i) { return texelFetch(DataSampler, ivec2(i, 0), 0); }
float u16(vec2 v) { return (floor(v.x * 255.0 + 0.5) * 256.0 + floor(v.y * 255.0 + 0.5)) / 65535.0; }
float hash(float n) { return fract(sin(n * 12.9898) * 43758.5453); }

float linearDepth(float d, float far) {
    float z = d * 2.0 - 1.0;
    return (2.0 * NEAR * far) / (far + NEAR - z * (far - NEAR));
}

// camera basis, filled in at the start of main()
vec3 gFwd, gRight, gUp;
float gTh, gAspect, gFar;

vec3 viewRay(vec2 tc) {
    vec2 n = tc * 2.0 - 1.0;
    return gFwd + gRight * (n.x * gTh * gAspect) + gUp * (n.y * gTh);
}

// camera-relative world position of the surface at this screen spot
vec3 posAt(vec2 tc) {
    return viewRay(tc) * linearDepth(texture(DepthSampler, tc).r, gFar);
}

// camera-relative world position -> screen uv (z = distance along the view direction)
vec3 project(vec3 p) {
    float z = dot(p, gFwd);
    vec2 n = vec2(dot(p, gRight) / (z * gTh * gAspect), dot(p, gUp) / (z * gTh));
    return vec3(n * 0.5 + 0.5, z);
}

// Minecraft is blocky: snap the reconstructed normal to the closest axis for clean, stable lighting
vec3 snapNormal(vec3 n) {
    vec3 a = abs(n);
    if (a.x > a.y && a.x > a.z) return vec3(sign(n.x), 0.0, 0.0);
    if (a.y > a.z) return vec3(0.0, sign(n.y), 0.0);
    return vec3(0.0, 0.0, sign(n.z));
}

void main() {
    vec4 d0 = dataAt(0), d1 = dataAt(1), d2 = dataAt(2), d3 = dataAt(3);
    vec4 d4 = dataAt(4), d5 = dataAt(5), d6 = dataAt(6), d7 = dataAt(7), d8 = dataAt(8);

    float sunElev = d0.r * 2.0 - 1.0;
    float morning = d0.g;
    float rain = d0.b;
    float thunder = d0.a;
    float pitch = radians(u16(d1.rg) * 180.0 - 90.0);
    float yaw = radians(u16(d1.ba) * 360.0);
    float camY = u16(d2.rg) * 512.0 - 128.0;
    float fov = radians(max(d2.b * 180.0, 30.0));
    float far = max(d2.a * 4096.0, 64.0);
    float under = d3.r;
    float heat = d3.g;
    float mistBiome = d3.b;
    float night = d3.a;
    float iSun = d4.r * 2.0, iHaze = d4.g * 2.0, iMist = d4.b * 2.0, iHeat = d4.a * 2.0;
    float iRays = d5.r * 2.0, iUwDepth = d5.g * 2.0, iStorm = d5.b * 2.0, iStreak = d5.a * 2.0;
    float iGrade = d6.r * 2.0, iBloom = d6.g * 2.0, iVig = d6.b * 2.0;
    float overworld = d6.a;
    float camSky = d7.r;
    float cave = d7.b;
    float daylight = d7.a;
    float iSunRays = d8.r * 2.0;
    float iShadow = d8.g * 2.0;
    float iAO = d8.b * 2.0;
    float time = GameTime * 1200.0; // seconds

    // ---- view ray for this pixel (world space, forward component = 1) ----
    vec2 ndc = texCoord * 2.0 - 1.0;
    float aspect = OutSize.x / OutSize.y;
    float th = tan(fov * 0.5);
    vec3 fwd = vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch));
    vec3 right = vec3(-cos(yaw), 0.0, -sin(yaw));
    vec3 up = vec3(-sin(yaw) * sin(pitch), cos(pitch), cos(yaw) * sin(pitch));
    vec3 ray = fwd + right * (ndc.x * th * aspect) + up * (ndc.y * th);
    vec3 dir = normalize(ray);
    gFwd = fwd; gRight = right; gUp = up; gTh = th; gAspect = aspect; gFar = far;

    vec2 uv = texCoord;

    // ---- heat shimmer: distant pixels near the horizon wobble a little ----
    float heatAmt = iHeat * heat * daylight * (1.0 - rain) * (1.0 - under) * overworld;
    if (heatAmt > 0.001) {
        float lz = linearDepth(texture(DepthSampler, uv).r, far);
        float farMask = smoothstep(14.0, 56.0, lz) * (1.0 - smoothstep(0.0, 0.3, abs(dir.y)));
        float w = sin(uv.y * OutSize.y * 0.08 + time * 5.0) * 0.6 + sin(uv.y * OutSize.y * 0.21 - time * 8.0) * 0.4;
        uv.x += w * (1.6 / OutSize.x) * farMask * heatAmt;
    }

    vec3 c = texture(InSampler, uv).rgb;
    float depth = texture(DepthSampler, uv).r;
    bool isSky = depth >= 0.99999;
    float lin = isSky ? far * 2.0 : linearDepth(depth, far);
    float dist = lin * length(ray);
    float pixelY = camY + ray.y * lin;

    // ---- replace vanilla's square sun with a round disc (done first, so all later colouring matches) ----
    vec3 sunDir0 = normalize(vec3((morning * 2.0 - 1.0) * sqrt(max(1.0 - sunElev * sunElev, 0.0)), sunElev, 0.0));
    // clouds are see-through, so the square shows through them too: treat far, high-up pixels like sky here
    bool cloudPx = !isSky && lin > 48.0 && pixelY > 130.0;
    if ((isSky || cloudPx) && iSunRays > 0.001 && overworld > 0.5 && under < 0.5) {
        vec3 e1 = vec3(0.0, 0.0, 1.0);
        vec3 e2 = normalize(cross(sunDir0, e1));
        float dd = dot(dir, sunDir0);
        vec2 off = vec2(dot(dir, e1), dot(dir, e2)) / max(dd, 0.001);
        float sq = max(abs(off.x), abs(off.y));
        if (dd > 0.0 && sq < 0.115) {
            // sky colour just outside the square: try 8 spots, keep the ones on screen that are open sky
            vec3 behind = vec3(0.0);
            float found = 0.0;
            for (int k = 0; k < 8; k++) {
                float ang = float(k) * 0.7854;
                vec3 q = sunDir0 * 100.0 + (e1 * cos(ang) + e2 * sin(ang)) * 15.0;
                vec3 qp = project(q);
                if (qp.z <= 0.0 || qp.x < 0.002 || qp.x > 0.998 || qp.y < 0.002 || qp.y > 0.998) continue;
                float qd = texture(DepthSampler, qp.xy).r;
                bool qSky = qd >= 0.99999;
                float qLin = linearDepth(qd, far);
                bool qCloud = !qSky && qLin > 48.0 && camY + viewRay(qp.xy).y * qLin > 130.0;
                // sky patches only from sky; cloud patches from cloud or sky
                if (isSky ? !qSky : !(qSky || qCloud)) continue;
                behind += texture(InSampler, qp.xy).rgb;
                found += 1.0;
            }
            float r = length(off);
            float disc = 1.0 - smoothstep(0.042, 0.052, r);
            vec3 discCol = vec3(1.0, 0.99, 0.95);
            // blend the patch in softly toward the square's edge so no seam is left
            float edgeBlend = 1.0 - smoothstep(0.1, 0.115, sq);
            if (found > 0.5) {
                vec3 patch = mix(behind / found, discCol, disc);
                c = mix(c, patch, max(edgeBlend, disc));
            } else {
                c = mix(c, discCol, max(disc, 0.85 * edgeBlend));
            }
        }
    }

    // sun direction (east in the morning, west in the evening); moon is opposite
    vec3 sunDirL = normalize(vec3((morning * 2.0 - 1.0) * sqrt(max(1.0 - sunElev * sunElev, 0.0)), sunElev, 0.0));

    // ---- shader-style lighting: sun/moon light, soft shadows and ambient occlusion (from the blurred lighting pass) ----
    bool lightable = !isSky && lin > 0.4 && dist < 110.0 && under < 0.5 && cave < 0.5 && overworld > 0.5;
    vec4 Lm = texture(LightSampler, uv);
    if (lightable && Lm.a > 0.5) {
        float fade = 1.0 - smoothstep(64.0, 110.0, dist);
        if (iAO > 0.001) {
            c *= mix(1.0, Lm.g, 0.6 * min(iAO, 2.0) * fade);
        }
        if (iShadow > 0.001) {
            bool moon = sunElev < 0.0;
            float lightUp = moon ? smoothstep(0.0, 0.3, -sunElev) * 0.55 : smoothstep(-0.02, 0.2, sunElev);
            float lit = Lm.b * Lm.r;
            vec3 sunTint = moon ? vec3(1.0, 1.08, 1.3) : mix(vec3(1.35, 0.98, 0.7), vec3(1.34, 1.24, 1.06), smoothstep(0.0, 0.45, sunElev));
            vec3 shadeTint = moon ? vec3(0.7, 0.75, 0.95) : vec3(0.46, 0.53, 0.74);
            vec3 lightMul = mix(shadeTint, sunTint, lit);
            float amt = iShadow * lightUp * fade * camSky * (1.0 - rain * 0.7) * 1.0;
            c *= mix(vec3(1.0), lightMul, clamp(amt, 0.0, 1.0));
        }
    }

    // ---- sunrise / sunset ----
    float horizon = 1.0 - smoothstep(0.04, 0.42, abs(sunElev + 0.06));
    float dawnDusk = 1.0 - smoothstep(0.0, 0.55, abs(sunElev));
    float sunset = horizon * overworld * (1.0 - under) * (1.0 - rain * 0.75) * iSun;
    vec3 sunDir = normalize(vec3((morning * 2.0 - 1.0) * sqrt(max(1.0 - sunElev * sunElev, 0.0)), sunElev, 0.0));
    float sunGlow = pow(max(dot(dir, sunDir), 0.0), 5.0);
    vec3 sunriseCol = vec3(1.0, 0.76, 0.66);   // soft gold + pink
    vec3 sunsetCol = vec3(1.0, 0.47, 0.22);    // warm red-orange
    vec3 tint = mix(sunsetCol, sunriseCol, morning);

    if (sunset > 0.001) {
        // landscape picks up the warm light
        c = mix(c, c * mix(vec3(1.0), tint * 1.12, 0.35), sunset);
        // sky and clouds (anything high up or very far) get a stronger tint toward the sun
        float skyW = isSky ? 1.0 : smoothstep(140.0, 185.0, pixelY) * smoothstep(48.0, 160.0, dist);
        c = mix(c, c * tint * 1.25, sunset * skyW * (0.35 + 0.45 * sunGlow));
        c += tint * sunGlow * sunset * (isSky ? 0.22 : 0.06);
    }

    // ---- bright sun: round glow, light shafts through leaves/grass/clouds, glare when facing it ----
    if (iSunRays > 0.001 && overworld > 0.5 && under < 0.5 && cave < 0.5 && sunElev > -0.12) {
        float facingSun = dot(sunDir, fwd);
        // full strength as soon as the sun clears the horizon, so dawn and dusk shine too
        float sunStrength = smoothstep(-0.1, 0.03, sunElev) * (1.0 - rain * 0.85) * iSunRays;
        // whiter at noon, warmer near the horizon
        vec3 sunCol = mix(vec3(1.0, 0.7, 0.42), vec3(1.0, 0.95, 0.85), smoothstep(0.0, 0.45, sunElev));
        vec3 sunAdd = vec3(0.0);
        if (facingSun > 0.05) {
            vec2 sunUV = vec2(dot(sunDir, right) / facingSun / (th * aspect), dot(sunDir, up) / facingSun / th) * 0.5 + 0.5;
            // march from this pixel toward the sun; open sky along the way = light gets through
            const int STEPS = 24;
            vec2 delta = (sunUV - texCoord) / float(STEPS);
            vec2 p = texCoord;
            float decay = 1.0;
            float shafts = 0.0;
            for (int i = 0; i < STEPS; i++) {
                p += delta;
                vec2 q = clamp(p, vec2(0.0), vec2(1.0));
                float open = texture(DepthSampler, q).r >= 0.99999 ? 1.0 : 0.0;
                float nearSun = exp(-length((q - sunUV) * vec2(aspect, 1.0)) * 4.5);
                shafts += open * nearSun * decay;
                decay *= 0.94;
            }
            shafts /= float(STEPS);
            float edgeFade = smoothstep(0.0, 0.35, facingSun);
            // shafts show up in the air in front of things; open sky is already bright so it gets less
            sunAdd += sunCol * shafts * (isSky ? 0.45 : 1.6) * edgeFade;

            // soft glow so the sun reads as a light source
            float dSun = length((texCoord - sunUV) * vec2(aspect, 1.0));
            float glow = exp(-dSun * 5.5) * 0.55 + exp(-dSun * 16.0) * 0.8 + exp(-dSun * 55.0) * 1.2;
            sunAdd += sunCol * glow * edgeFade * (isSky ? 1.0 : 0.08);
        }
        // screen blend: brightens without blowing everything out to flat white
        c = 1.0 - (1.0 - clamp(c, 0.0, 1.0)) * (1.0 - clamp(sunAdd * sunStrength, 0.0, 0.98));
        // looking straight at the sun washes the view out a touch
        float glare = pow(max(facingSun, 0.0), 12.0);
        c *= 1.0 + glare * sunStrength * 0.25;
        c = mix(c, vec3(1.0, 0.98, 0.94), glare * sunStrength * 0.18);
        // daylight is just brighter overall, like real sunshine
        c *= 1.0 + 0.1 * sunStrength * smoothstep(0.1, 0.6, sunElev);
    }

    // ---- moon: soft silver glow at night ----
    if (iSunRays > 0.001 && overworld > 0.5 && under < 0.5 && cave < 0.5 && sunElev < 0.1) {
        vec3 moonDir = -sunDir;
        float moonUp = smoothstep(-0.05, 0.15, moonDir.y) * (1.0 - rain * 0.85) * iSunRays;
        float facingMoon = dot(moonDir, fwd);
        if (facingMoon > 0.05 && moonUp > 0.001) {
            vec2 moonUV = vec2(dot(moonDir, right) / facingMoon / (th * aspect), dot(moonDir, up) / facingMoon / th) * 0.5 + 0.5;
            float dMoon = length((texCoord - moonUV) * vec2(aspect, 1.0));
            float mglow = exp(-dMoon * 8.0) * 0.2 + exp(-dMoon * 26.0) * 0.35 + exp(-dMoon * 80.0) * 0.3;
            vec3 moonCol = vec3(0.72, 0.8, 1.0);
            vec3 madd = moonCol * mglow * (isSky ? 1.0 : 0.15) * moonUp * smoothstep(0.0, 0.35, facingMoon);
            c = 1.0 - (1.0 - clamp(c, 0.0, 1.0)) * (1.0 - clamp(madd, 0.0, 0.8));
        }
    }

    // ---- light atmospheric haze, stronger at dawn and dusk ----
    if (!isSky && iHaze > 0.001) {
        vec3 dayHaze = vec3(0.78, 0.84, 0.92);
        vec3 hazeCol = mix(dayHaze, tint, dawnDusk) * (0.08 + 0.92 * daylight);
        float hazeF = 1.0 - exp(-dist / (far * 0.5));
        float amt = iHaze * (0.12 + 0.88 * dawnDusk) * overworld * (1.0 - under) * (1.0 - cave);
        c = mix(c, hazeCol, clamp(hazeF * amt * 0.4, 0.0, 0.5));
    }

    // ---- ground mist: hugs the ground in swamps/forests and pools in valleys ----
    if (!isSky && iMist > 0.001 && under < 0.5) {
        float aboveFeet = pixelY - (camY - 1.4);
        float heightF = exp(-max(aboveFeet, 0.0) / 2.2);
        float lowF = 1.0 - smoothstep(58.0, 78.0, pixelY);
        float timeBoost = 0.35 + 0.65 * max(night, dawnDusk);
        float distF = 1.0 - exp(-dist / 38.0);
        vec2 wp = ray.xz * lin;
        float drift = 0.8 + 0.2 * sin(wp.x * 0.15 + time * 0.25) * sin(wp.y * 0.12 - time * 0.18);
        float amt = iMist * timeBoost * distF * drift * clamp(mistBiome * heightF + lowF * 0.5 * (dawnDusk + night * 0.5), 0.0, 1.0);
        amt *= overworld * (1.0 - cave) * 0.55;
        vec3 mistCol = mix(vec3(0.72, 0.76, 0.8) * (0.12 + 0.88 * daylight), tint * 0.95, sunset * 0.5);
        c = mix(c, mistCol, clamp(amt, 0.0, 0.6));
    }

    // ---- storm fog ----
    if (iStorm > 0.001 && (rain > 0.01 || thunder > 0.01) && under < 0.5) {
        float f = 1.0 - exp(-dist / (far * (0.35 - thunder * 0.15)));
        float amt = iStorm * (rain * 0.25 + thunder * 0.45) * (1.0 - cave);
        vec3 stormCol = vec3(0.33, 0.36, 0.41) * (0.15 + 0.85 * daylight);
        c = mix(c, stormCol, clamp(f * amt, 0.0, 0.6));
    }

    // ---- wind-driven storm rain streaks (blocky, 2px columns) ----
    if (iStreak > 0.001 && rain > 0.05 && under < 0.5 && cave < 0.5 && camSky > 0.4 && lin > 2.0) {
        vec2 p = floor(uv * OutSize / 2.0);
        float slant = 0.45 + thunder * 0.4;
        float col = floor(p.x + p.y * slant);
        float rnd = hash(col);
        float seg = fract(p.y / (OutSize.y / 2.0) * (2.0 + rnd * 2.0) + time * (2.2 + rnd * 1.5) + rnd * 13.0);
        float streak = step(0.965, rnd) * step(seg, 0.18);
        float amt = streak * iStreak * rain * (0.35 + thunder * 0.65) * 0.22;
        c = mix(c, vec3(0.78, 0.82, 0.9) * (0.35 + 0.65 * daylight), amt);
    }

    // ---- underwater: depth color & god rays ----
    if (under > 0.01) {
        float f = 1.0 - exp(-dist / 28.0);
        float f2 = 1.0 - exp(-dist / 48.0);
        c = mix(c, c * vec3(0.7, 0.88, 0.95), f2 * iUwDepth * 0.3 * under);
        if (iRays > 0.001) {
            float a = atan(dir.z, dir.x);
            float s1 = sin(a * 13.0 + time * 0.35);
            float s2 = sin(a * 21.0 - time * 0.55 + 1.7);
            float shaft = pow(max(s1 * s2, 0.0), 2.0);
            float lookUp = smoothstep(-0.25, 0.85, dir.y);
            float volume = 1.0 - exp(-min(dist, 40.0) / 10.0);
            c += vec3(0.5, 0.72, 0.78) * shaft * lookUp * volume * camSky * daylight * 0.2 * iRays * under;
        }
    }

    // ---- bloom (screen blend, gentle) ----
    vec3 bloom = texture(BloomSampler, uv).rgb;
    c = 1.0 - (1.0 - clamp(c, 0.0, 1.0)) * (1.0 - bloom * 0.35 * iBloom);

    // ---- deeper, richer daytime sky ----
    if (isSky && overworld > 0.5 && under < 0.5 && iGrade > 0.001) {
        float l = dot(c, LUMA);
        vec3 rich = mix(vec3(l), c, 1.25) * mix(vec3(1.0), vec3(0.9, 0.97, 1.08), daylight * (1.0 - sunset));
        float upness = smoothstep(0.0, 0.7, dir.y);
        c = mix(c, rich * (1.0 - upness * 0.12), 0.7 * min(iGrade, 1.0));
    }

    // ---- warm cinematic grade ----
    if (iGrade > 0.001) {
        vec3 g = clamp(c * 1.03, 0.0, 1.0);
        vec3 s = g * g * (3.0 - 2.0 * g);
        g = mix(g, s, 0.3);
        float l = dot(g, LUMA);
        g *= mix(vec3(0.95, 0.98, 1.04), vec3(1.05, 1.0, 0.91), smoothstep(0.0, 0.7, l));
        l = dot(g, LUMA);
        g = mix(vec3(l), g, 1.12);
        c = mix(c, g, iGrade);
    }

    // ---- vignette ----
    vec2 dv = texCoord - 0.5;
    c *= 1.0 - smoothstep(0.38, 0.9, length(dv * vec2(1.1, 1.0))) * 0.32 * iVig;

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
