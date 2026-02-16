<!-- Wargames Development Group – Foliage+ -->

<!-- Badges (enable when/if you publish on these platforms) -->
<!-- [![CurseForge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg)]() -->
<!-- [![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg)]() -->

[![Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.wargames.uk)

# Foliage+ (Forge 1.7.10)

**Foliage+** is a lightweight Forge **1.7.10** mod that makes leaf blocks behave as dense foliage rather than solid terrain:
- Players (and optionally all entities) can **move through leaves**
- Movement can be **slowed** while inside foliage (configurable multiplier)
- Movement through foliage produces **very loud rustling audio** to discourage stealth/ambush abuse

Repository: https://github.com/Wargames-Development/foliage-plus

---

## Features

### Passable leaves (vanilla)
- All vanilla leaf types are made **non-collidable**:
    - Oak
    - Spruce
    - Birch
    - Jungle
    - Acacia
    - Dark Oak

### Configurable behavior
- Toggle the mod on/off
- Choose whether pass-through applies to:
    - **Players only**, or
    - **All entities**
- Adjust slowdown using a **speed multiplier** (`0.01`–`1.00`)
- Optional “dense foliage” behavior:
    - When full cover is disabled, the **bottom block of a 2-high leaf column becomes solid again**
    - Prevents fully embedding inside bushes while still allowing leaf-tops to be used as terrain

### Loud rustling audio (anti-stealth)
- Movement through leaves triggers **very loud rustling sounds**
- Multiple sound variants are randomized for less repetition
- Audio is tuned to avoid harsh stereo snapping/clipping while strafing

---

## Configuration

Foliage+ generates a standard Forge config file under the normal config directory (same location as other mods).

Settings include:
- Enable / disable the mod
- Players-only vs all-entities behavior
- Full-cover foliage behavior toggle
- Movement slowdown multiplier (`0.01`–`1.00`)

---

## Installation

### Client
1. Install Forge **1.7.10 (10.13.4.1614)**
2. Drop the Foliage+ jar into your `mods/` folder
3. Launch the game

### Dedicated Server
1. Install Forge **1.7.10 (10.13.4.1614)**
2. Drop the Foliage+ jar into your server `mods/` folder
3. Start the server

> Note: Foliage+ ships as a single combined **mod + coremod** jar for 1.7.10 compatibility.  
> It should still be placed in the normal `mods/` folder.

---

## Building from Source

If you wish to compile the mod yourself:

### Requirements
- Java 8 JDK
- Git
- Windows, Linux, or macOS

### Steps

1. Clone the repository:

       git clone https://github.com/Wargames-Development/foliage-plus.git

2. Navigate into the project directory.

3. Build the mod:

       gradlew build

4. The compiled JAR will be located in:

       build/libs/

> Note: Development builds may be unstable and are not guaranteed to match release behavior.

---

## Contributing

Contributions are welcome from anyone with Forge modding experience.

Please note:
- This project targets **Minecraft 1.7.10** specifically.
- Familiarity with ForgeGradle, ASM, and legacy Forge APIs is strongly recommended.

### General Guidelines
- Keep changes focused and well-documented.
- Avoid unnecessary refactors.
- Test changes in both singleplayer and multiplayer environments where applicable.

If you would like to contribute in a more official capacity, please contact us through our Discord server.

---

## Need to get in touch?

Our primary community hub is our Discord server:

https://discord.wargames.uk

For non-support enquiries:
- **dev@wargames.uk** — development / project enquiries
- **abuse@wargames.uk** — security or abuse reports

> Please note: email is **not** used for mod support.  
> Use Discord for questions, feedback, or bug reports.

---

## Credits

### Development
This mod is developed and maintained by the **Wargames Development Group (WDG)**.

Primary development:
- **Glac** — Lead developer  
  https://github.com/RhysHopkins04

### Contributors

[![Contributors](https://contrib.rocks/image?repo=Wargames-Development/foliage-plus)](https://github.com/Wargames-Development/foliage-plus/graphs/contributors)

---

### Acknowledgements

While all code in this repository is original, implementation approaches were informed by:
- Common Forge 1.7.10 modding practices
- Observed behavior in large legacy mods (e.g. HBM-style sound handling)

No third-party code has been directly copied.

---

### Wargames Development Group Team

- [Glac](https://github.com/RhysHopkins04) - Developer
- [Barrack](https://github.com/BateNacon) - Developer
- [Ocean](https://github.com/Oceanseaj) - Advisor
- [Viking](https://github.com/snowboardman91) - Advisor

---

## License & Usage

This project is provided as-is for use in modpacks and private servers.

Please respect the authorship of this repository when redistributing or modifying the code.

---

## Links

- GitHub Repository: https://github.com/Wargames-Development/foliage-plus
- Discord: https://discord.wargames.uk

---
