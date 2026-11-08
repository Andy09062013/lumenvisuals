#version 330

// Lumen lighting pass: screen-space sun shadows + ambient occlusion into a small mask
// (r = sun visibility, g = ambient occlusion, b = how much the face points at the sun, a = valid).
// It gets blurred (depth-aware) before use, so the jittered rays come out soft instead of streaky.

uniform sampler2D DepthSampler;
uniform sampler2D DataSampler;
uniform sampler2D ShadowSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

const float NEAR = 0.05;

vec4 dataAt(int i) { return texelFetch(DataSampler, ivec2(i, 0), 0); }
float u16(vec2 v) { return (floor(v.x * 255.0 + 0.5) * 256.0 + floor(v.y * 255.0 + 0.5)) / 65535.0; }

float linearDepth(float d, float far) {
    float z = d * 2.0 - 1.0;
    return (2.0 * NEAR * far) / (far + NEAR - z * (far - NEAR));
}

vec3 gFwd, gRight, gUp;
float gTh, gAspect, gFar;

vec3 viewRay(vec2 tc) {
    vec2 n = tc * 2.0 - 1.0;
    return gFwd + gRight * (n.x * gTh * gAspect) + gUp * (n.y * gTh);
}

vec3 posAt(vec2 tc) {
    return viewRay(tc) * linearDepth(texture(DepthSampler, tc).r, gFar);
}

vec3 project(vec3 p) {
    float z = dot(p, gFwd);
    vec2 n = vec2(dot(p, gRight) / (z * gTh * gAspect), dot(p, gUp) / (z * gTh));
    return vec3(n * 0.5 + 0.5, z);
}

vec3 snapNormal(vec3 n) {
    vec3 a = abs(n);
    if (a.x > a.y && a.x > a.z) return vec3(sign(n.x), 0.0, 0.0);
    if (a.y > a.z) return vec3(0.0, sign(n.y), 0.0);
    return vec3(0.0, 0.0, sign(n.z));
}

// small per-pixel noise; the blur pass smooths it out
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

void main() {
    vec4 d0 = dataAt(0), d1 = dataAt(1), d2 = dataAt(2), d3 = dataAt(3), d6 = dataAt(6), d7 = dataAt(7), d8 = dataAt(8);
    vec4 d9 = dataAt(9), d10 = dataAt(10);
    bool mapReady = d8.a > 0.5;
    float sunElev = d0.r * 2.0 - 1.0;
    float morning = d0.g;
    float pitch = radians(u16(d1.rg) * 180.0 - 90.0);
    float yaw = radians(u16(d1.ba) * 360.0);
    float fov = radians(max(d2.b * 180.0, 30.0));
    float far = max(d2.a * 4096.0, 64.0);
    float under = d3.r;
    float overworld = d6.a;
    float cave = d7.b;
    float iShadow = d8.g * 2.0;
    float iAO = d8.b * 2.0;

    gFwd = vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch));
    gRight = vec3(-cos(yaw), 0.0, -sin(yaw));
    gUp = vec3(-sin(yaw) * sin(pitch), cos(pitch), cos(yaw) * sin(pitch));
    gTh = tan(fov * 0.5);
    gAspect = OutSize.x / OutSize.y;
    gFar = far;

    vec2 uv = texCoord;
    float depth = texture(DepthSampler, uv).r;
    float lin = linearDepth(depth, far);
    vec3 ray = viewRay(uv);
    float dist = lin * length(ray);

    if (depth >= 0.99999 || lin < 0.4 || dist > 110.0 || under > 0.5 || cave > 0.5 || overworld < 0.5 || (iShadow < 0.001 && iAO < 0.001)) {
        fragColor = vec4(1.0, 1.0, 0.0, 0.0);
        return;
    }

    vec2 px = vec2(1.0 / OutSize.x, 0.0), py = vec2(0.0, 1.0 / OutSize.y);
    vec3 P = ray * lin;
    vec3 pr = posAt(uv + px), pl = posAt(uv - px), pu = posAt(uv + py), pd = posAt(uv - py);
    vec3 dx = abs(dot(pr - P, gFwd)) < abs(dot(P - pl, gFwd)) ? pr - P : P - pl;
    vec3 dy = abs(dot(pu - P, gFwd)) < abs(dot(P - pd, gFwd)) ? pu - P : P - pd;
    float edge = max(abs(dot(dx, gFwd)), abs(dot(dy, gFwd)));
    if (edge > 0.25 + lin * 0.02) {
        fragColor = vec4(1.0, 1.0, 0.0, 0.0);
        return;
    }
    vec3 N = normalize(cross(dx, dy));
    if (dot(N, P) > 0.0) N = -N;
    N = snapNormal(N);

    float noise = ign(floor(uv * OutSize));

    // ---- ambient occlusion ----
    float ao = 1.0;
    if (iAO > 0.001) {
        const int AO_N = 8;
        float occ = 0.0;
        vec3 T = normalize(abs(N.y) > 0.5 ? cross(N, vec3(1.0, 0.0, 0.0)) : cross(N, vec3(0.0, 1.0, 0.0)));
        vec3 B = cross(N, T);
        float rot = noise * 6.2831;
        for (int i = 0; i < AO_N; i++) {
            float fi = float(i);
            float a = fi * 2.39996 + rot;
            float r = 0.15 + 0.6 * fract(fi * 0.618 + noise);
            vec3 S = P + N * (0.06 + 0.3 * fract(fi * 0.381 + noise)) + (T * cos(a) + B * sin(a)) * r;
            vec3 sp = project(S);
            if (sp.x < 0.0 || sp.x > 1.0 || sp.y < 0.0 || sp.y > 1.0 || sp.z <= NEAR) continue;
            float sceneZ = linearDepth(texture(DepthSampler, sp.xy).r, far);
            float diff = sp.z - sceneZ;
            occ += (diff > 0.03 && diff < 1.0) ? 1.0 : 0.0;
        }
        ao = 1.0 - occ / float(AO_N);
    }

    // ---- sun (or moon) shadow ----
    bool moon = sunElev < 0.0;
    vec3 sunDir = normalize(vec3((morning * 2.0 - 1.0) * sqrt(max(1.0 - sunElev * sunElev, 0.0)), sunElev, 0.0));
    vec3 L = moon ? -sunDir : sunDir;
    if (mapReady) L = normalize(vec3(u16(d10.rg) * 2.0 - 1.0, u16(d10.ba) * 2.0 - 1.0, 0.0));
    float ndl = max(dot(N, L), 0.0);
    float vis = 1.0;
    if (iShadow > 0.001 && ndl > 0.0) {
        bool done = false;
        if (mapReady) {
            // real shadow map, drawn from the sun this frame
            const float R = 80.0;
            const float D = 192.0;
            const float TEXEL = 2.0 * R / 2048.0;
            vec3 X = vec3(0.0, 0.0, 1.0);
            vec3 Y = normalize(cross(L, X));
            float offX = u16(d9.rg) * TEXEL, offY = u16(d9.ba) * TEXEL;
            vec3 Pb = P + N * (0.06 + lin * 0.004);
            vec2 suv = vec2(dot(Pb, X) + offX, dot(Pb, Y) + offY) / R * 0.5 + 0.5;
            float sd = (-dot(Pb, L) / D) * 0.5 + 0.5;
            if (suv.x > 0.001 && suv.x < 0.999 && suv.y > 0.001 && suv.y < 0.999 && sd < 1.0) {
                float bias = 0.0006;
                float lit = 0.0;
                float a = noise * 6.2831;
                for (int i = 0; i < 4; i++) {
                    float ang = a + float(i) * 1.5708;
                    vec2 o = vec2(cos(ang), sin(ang)) * (0.9 / 2048.0) * (1.0 + float(i & 1));
                    lit += texture(ShadowSampler, suv + o).r + bias >= sd ? 1.0 : 0.0;
                }
                vis = lit * 0.25;
                done = true;
            }
        }
        if (!done) {
            // fallback outside the shadow map: screen-space rays
            const int STEPS = 20;
            float stepLen = 0.22 + lin * 0.01;
            vec3 S = P + N * (0.05 + lin * 0.002) + L * stepLen * noise;
            for (int i = 0; i < STEPS; i++) {
                S += L * stepLen;
                vec3 sp = project(S);
                if (sp.x < 0.0 || sp.x > 1.0 || sp.y < 0.0 || sp.y > 1.0 || sp.z <= NEAR) break;
                float sceneZ = linearDepth(texture(DepthSampler, sp.xy).r, far);
                float diff = sp.z - sceneZ;
                float thickness = 1.5 + sp.z * 0.02;
                if (diff > 0.05 + sp.z * 0.003 && diff < thickness) { vis = 0.0; break; }
            }
        }
    }

    fragColor = vec4(vis, ao, ndl, 1.0);
}
