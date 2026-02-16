# **TEMP DOCUMENT**

## Why?

- This document is here to produce the steps to properly onboard your mod repo based on this template.
- Since there are specific things you need to change this will guide you through it!

## Introduction

This template is made almost entirely for the Wargames Development Group, to streamline proper mod versioning by somewhat force, <br>
However if wanted then other people may use it.
You can use this template either by forking and then breaking the link to this, <br>
*OR* by downloading this as a file and setting up that way without any links.

> [!TIP]
> *The latter is recommended.*

---

## Getting Started

1. Firstly Change the name of the root folder downloaded.
2. Next open the `build.gradle` and check the below
    * `group = "com.yourname"` should be com.<your name>
    * `sourceCompatibility = targetCompatibility = '1.8'` Should be set to the correct java version.
    * `mcVersion = "1.7.10"` Should be set to the correct Minecraft Version you are using (This shouldn't and can't really be changed).
    * `version = "${mcVersion}-10.13.4.1614-1.7.10"` The long string should be the correct latest minecraft forge 1.7.10 build.
    * `dependencies {}` Will need to be changed to impliment the correct dependencies for running the game and building, they are some standard ones.

3. Now open the `gradle.properties` and change the below, *follow the instructions in there*
    * `modBaseName=modid` Should be changed to the short modid.
    * `modDisplayName=Mod Pretty Name` Should be changed to the full pretty print name of the mod.
    * `modMainVersion=1` Will likely be left at 1, but is the initial version in the name.
    * `modSubVersion=0` Will also likely be left at 0 intially, but can be incrimented in development per minor release
    **IF** left at default the mod file would be: `modid-1.0.0`, and the data in mcmod.info would follow as above, the rest needs to be set manually.

4. You will want to select the license you want for the mod, ensure you follow assets license restricitons.
    * This can be done either now by making a `LICENSE.txt` and pasting in a license, OR when initializing the repo on github.

5. Navigate to `/src/main/resources/mcmod.info` and fill in the below.
    * `description` The long text description of the mod
    * `url` A URL link to your website or any other discord etc support forum.
    * `updateUrl` Can be left untouched since it doesnt have a functionality yet
    * `authorList` Add the authors of the mod in here, but for multiple authors it should look like this: `"authorList": ["Author1", "Author2"],` etc.
    * `credits` Add a text string for credits to any assets, textures or help.
    * `logoFile` Path to a logo file, must be .png, if it is in the root just put `/logo.png` if it isnt then `/path/to/logo.png`
    * `screenshots` Can be left untouched since it doesnt have a functionality yet
    * `dependencies` Soft dependencies – mods that should be loaded before your mod.

> [!NOTE]
> There are other's that you can use, but are commonly not used.
> More info can be found [here](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/modification-development/2405990-mcmod-info-file-guide-and-help)

> [!CAUTION]
> **DO NOT EDIT THOSE WITH ${variable} unless you want the previous steps to fail...**

6. Check the pathing for the java `/src/main/java/com/<your name>` as was set in step `2.1`

7. You can now edit the README.md File, this is self explanitory so i wont go through its steps.

> [!NOTE]
> We recommend you clear line 1 post completion, Then go through and
> Check *all* links and sections carefully to ensure they are filled with correct information.

8. If you are unfamiliar with minecraft modding, see below:
    * Check over the README.md's contributing section,
    * Or if the instructions are not clear enough look at youtube videos.

## Seriously, thats it, you can now proceed with deleting the this file, and publishing on github
