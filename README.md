# Lumen

Shader-like visuals for Minecraft 1.21.11 (Fabric). Purely cosmetic.

**Download:** [lumen.jar (latest)](https://github.com/Andy09062013/lumenvisuals/raw/main/download/lumen.jar) - put it in your mods folder with [Fabric API](https://modrinth.com/mod/fabric-api).

**Best with [Distant Horizons](https://modrinth.com/mod/distanthorizons) (optional, recommended).**

```
LUMEN v0.6 - visual mod for Minecraft 1.21.11 (Fabric)

BUILD: double click BUILD.bat. The jar ends up in the out folder.
INSTALL: put the jar + Fabric API in your mods folder (client). Optionally on a server too.
SETTINGS: press F6 (or type /lumen in chat). Presets Low / Medium / High / Ultra, plus every effect on/off with a strength slider.

Everything is cosmetic. No gameplay, physics, hitboxes, light levels or world gen are changed.

WATER
- Splashes when players, mobs, items fall in (bigger for higher falls, faster and bigger entities)
- Ripples around anything moving at the surface, swimming foam + droplets at your arms and feet
- Boat wakes, bubbles around swimmers
- Waterfalls: rising mist, spray and white foam at the bottom, scaled by waterfall height
- Drips near waterfalls and on wet cave ceilings (water above, lush/dripstone caves)
- Droplets and mist use the local biome water color

SKY
- Sunsets: red-orange tint on sky, clouds and landscape, glow toward the sun
- Sunrises: softer gold and pink
- Bright sun: round glow, light shafts that shine through gaps in leaves, grass and clouds, glare when you look at it
- Haze at dawn and dusk, all blended smoothly with the time of day
- Water mirrors the sky color at low angles (shader pack, see below)

AMBIENCE
- More falling leaves (uses vanilla's own leaf particles so colors match)
- Pollen in flowery biomes, fireflies at night in grassy places and swamps
- Ground mist in swamps, forests and low valleys (strongest at night and dawn)
- Drifting dust in deserts/badlands and caves
- Heat shimmer in hot dry biomes (optional, off on Low/Medium)
- Snow gusts in cold biomes
- Leaves, grass, crops and dead bushes lean gently downwind and spring back (shader pack)
- Breezes every 1-2 minutes: soft leaf rustle, loud only when you're surrounded by trees (/lumen breeze plays one)

WEATHER
- Rain splashes on ground and water, trees drip while it rains
- Storm fog and wind-driven rain streaks (streaks are optional, off on Low/Medium/High)
- Denser, wind-blown snowstorms
- Everything fades in and out with the rain instead of popping

UNDERWATER
- Drifting specks, light rays when you look up, deeper color with distance (visibility unchanged)

LIGHT & LOOK (shaderpack style)
- Cozy fullbright (off by default, first page of settings): dark places get warm torch-like light instead of flat brightness
- Fullbright resource packs (like Vanilla Tweaks Fullbright) are turned off automatically
- Fire and campfires sometimes burst sparks with a crackle sound
- Sun & moon lighting: sunlit faces get warm light, faces away from the sun get cool shade
- Shadows from anything on screen (trees, buildings, mobs) cast in the sun's direction
- Ambient occlusion: corners and crevices darken softly
- Richer, deeper daytime sky
- Torches/lanterns/glowstone etc light up the area when you or other players hold them (visual only)
- Warm color grading, gentle bloom, vignette

SHADER PACKS (Options > Resource Packs, or in /lumen)
Four built-in packs, all on by default:
  Lumen: swaying grass & foliage / sky colors on water / warm torchlight & blue moonlight / soft fog

SERVER
If Lumen is on a dedicated server, players without it get disconnected with a message telling
them to install it (a server can't install mods for them). Edit config/lumen-server.json:
  requireClientMod  true/false
  downloadUrl       the link shown to players
  kickMessage       the text shown to players
Players with Lumen can still join vanilla servers normally.

COMPATIBILITY
- Distant Horizons (recommended): far LOD terrain gets distance haze and blends into the sky; sun glow and light
  shafts no longer shine through distant mountains.
- These are screen-space tricks (like some lighter shaderpacks): shadows only come from things you can see on screen,
  and true reflections/volumetric clouds aren't possible without a full shader renderer like Iris.
- Iris: if a shaderpack is running, Lumen's screen effects turn off (the shaderpack does its own). Particles keep working.
- Sodium (built against 0.8.15 for 1.21.11): particles, screen effects, warm lighting, grass sway, water sky colors,
  clearer water and soft fog all work. Leaf sway and held-item light on blocks don't, and shadows fall back to the
  screen-space version. These show up greyed out in /lumen when Sodium is installed. A future Sodium update that
  changes its shaders may need a Lumen update.
- Particle amount scales with your Particles video setting and the preset.
```

## Installer
`download/LumenInstaller.exe` installs Lumen into the Minecraft Launcher, Prism/MultiMC, Modrinth App, CurseForge or Dawn (formerly Feather). It bundles Lumen, Fabric API (Apache-2.0, https://github.com/FabricMC/fabric) and Distant Horizons (LGPL-3.0, https://gitlab.com/distant-horizons-team/distant-horizons) so it never downloads anything. Source: `installer-go/`.
