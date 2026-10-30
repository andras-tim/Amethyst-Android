<h1 align="center">Amethyst::Dras</h1>

A fork of [Angel Aura Amethyst](https://github.com/AngelAuraMC/Amethyst-Android) that keeps a profile in sync with a
[packwiz](https://packwiz.infra.link/) modpack and adds profiles from `amethyst://` links. Built for the mc.dras.hu
family Minecraft server and its [modpack](https://github.com/andras-tim/mc-dras-modpack); not affiliated with the
Amethyst team. Everything below the line is the upstream README.

## Download

**[Latest release](https://github.com/andras-tim/Amethyst-Android/releases/latest)** → `amethyst-dras-<version>.apk`.

- It installs next to an official Amethyst: own application ID `hu.dras.amethyst`, name "Amethyst::Dras".
- Update by installing a newer release over it; the data stays (every release is signed with the same key,
  certificate SHA-256 `29:6E:B0:B2:F9:FD:3E:EA:8D:26:07:F0:C0:B9:5D:A8:21:0E:75:40:E5:00:08:86:1C:3D:3A:A4:DE:46:D7:64`).
- Java 17 and Java 8 are inside the APK for arm64 phones; other devices download them on first use, as upstream does.

## What the fork adds

- **packwiz pack per profile.** A profile with a pack URL (profile editor: "Packwiz pack URL") syncs before every
  Play: only changed files are downloaded and checked by their hash, files the pack dropped are removed unless the
  player changed them, worlds and options stay. The profile follows the pack's Minecraft and mod loader version. A
  failed sync names the file and URL, offers its log for sharing, and lets you play the installed version when
  offline. The log of the last sync is `<instance>/.packwiz/last-sync.log`.
- **Links.** A link (or the QR code of one) opens in the launcher and always asks before changing anything:
  - `amethyst://profile/import?url=<pack.toml URL>[&name=<profile name>][&icon=<png URL>][&ram=<MB>][&renderer=<id>]
    [&control=<layout file>][&java=<runtime>]` adds a profile for the pack and selects it (an existing profile of the
    same pack is selected instead). The pack decides what the game is: Minecraft, the loader, the mods and the configs.
    The parameters only decide how it runs on this device, so none of them can change the versions;
  - `amethyst://settings/set?resolutionRatio=<percent>` changes a launcher setting, for every profile. No other setting
    can be reached from a link;
  - `amethyst://profile/update?url=<new pack.toml URL>[&from=<old URL>][&name=<profile name>]` points a pack profile
    at another URL, keeping its worlds and settings.
  - the debug build uses `amethyst-debug://`, so it never competes with a release build for a link.
- **Memory per profile.** "Memory (MB)" in the profile editor overrides the global setting for that profile.
- **Adding a pack by hand.** "Create packwiz pack profile (URL)" in the new profile list asks for the `pack.toml` URL
  and then works like the import link.
- **Java 17 and Java 8 bundled.** Minecraft 1.20.1 runs on Java 17. The Forge installer must run on Java 8: on Java 17
  its jarsplitter step writes different bytes on some phones and stops with "Processor failed, invalid outputs" (seen
  on a OnePlus 7T Pro, not on a Galaxy S25 Ultra; [PojavLauncher#3772](https://github.com/PojavLauncherTeam/PojavLauncher/issues/3772)).
  Before installing Forge the launcher waits until the bundled Java 8 is unpacked, and the installer picks it as the
  nearest runtime.

## Versions

The `dras` branch is an upstream release tag plus this fork's commits: `feat`/`fix` commits are general and could go
upstream, `fork:` commits (branding, CI) belong here only. A release is tagged `<upstream version>-dras.<n>` (e.g.
`1.1.7-dras.1`); CI then publishes the signed APK. For a new upstream release the branch is rebased onto its tag
(`git rebase --onto <new tag> <old tag> dras`), built and tested, and released as `<new version>-dras.1`.

---

<h1 align="center">Angel Aura Amethyst</h1>

<img src="https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/assets/amethyst.png" align="left" width="130" height="130" alt="Amethyst logo">

[![Android CI](https://github.com/AngelAuraMC/Amethyst-Android/workflows/Android%20CI/badge.svg)](https://github.com/AngelAuraMC/Amethyst-Android/actions)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/AngelAuraMC/Amethyst-Android)](https://github.com/AngelAuraMC/Amethyst-Android/actions)
[![Crowdin](https://badges.crowdin.net/pojavlauncher/localized.svg)](https://crowdin.com/project/pojavlauncher)
[![Discord](https://img.shields.io/discord/724163890803638273.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/5ptqkyZxEy)

*From [Boardwalk](https://github.com/zhuowei/Boardwalk)'s ashes and [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)'s ruined reputation, here comes Amethyst!*

Amethyst is a launcher that allows you to play Minecraft: Java Edition on your Android and [iOS](https://github.com/AngelAuraMC/Amethyst-iOS) devices.

For more details, check out our [wiki](https://wiki.angelauramc.dev)!

## Table of Contents

* [Introduction](#introduction)
* [Getting Amethyst](#getting-amethyst)
* [Building](#building)
    * [Quick Build (Recommended)](#quick-build-recommended)
    * [Detailed Build](#detailed-build)
* [Current Status](#current-status)
* [Known Issues](#known-issues)
* [FAQ](#faq)
* [Contributing](#contributing)
* [Support](#support)
* [License](#license)
* [Credits & Dependencies](#credits--dependencies)
* [Roadmap](#roadmap)

## Introduction

* Amethyst is a Minecraft: Java Edition launcher for Android and iOS based on [Boardwalk](https://github.com/zhuowei/Boardwalk) and [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
* This launcher can launch almost all available Minecraft versions ranging from rd-132211 to 1.21 snapshots (including Combat Test versions)
* Modding via Forge and Fabric are also supported.
* This repository contains source code for Android. For iOS/iPadOS, check out [Amethyst-iOS](https://github.com/AngelAuraMC/Amethyst-iOS).

## Getting Amethyst

You can get Amethyst via two methods:

1. **Releases:** Download the latest prebuilt app from [nightly.link](https://nightly.link/AngelAuraMC/Amethyst-Android/workflows/android/v3_openjdk/app-debug%20%28recommended%29.zip) or select an older version from our [automatic builds](https://github.com/AngelAuraMC/Amethyst-Android/actions).
2. **Build from Source:** Follow the [building instructions](#building) below.

## Building

### Quick Build (Recommended)

The easiest way to build Amethyst is to use the pre-built JREs provided by our CI.

1. Clone the repository: `git clone --recursive https://github.com/AngelAuraMC/Amethyst-Android.git`
2. Build the launcher: `./gradlew :app_pojavlauncher:assembleDebug` (Use `gradlew.bat` on Windows)

The built APK will be located in `app_pojavlauncher/build/outputs/apk/debug/`.

### Detailed Build

If you need more control over the build process, follow these steps:

1. **Java Runtime Environment (JRE):** Download the `jre8-pojav` artifact from our [CI auto builds](https://github.com/AngelAuraMC/openjdk-build-multiarch/actions).  This package contains pre-built JREs for all supported architectures.  If you need to build the JRE yourself, follow the instructions in the [android-openjdk-build-multiarch](https://github.com/AngelAuraMC/openjdk-build-multiarch) repository.

2. **LWJGL:** The build instructions for the custom LWJGL are available over the [LWJGL repository](https://github.com/AngelAuraMC/lwjgl3).

3. **Language List:** Because languages are auto-added by Crowdin, you need to run the language list generator before building. In the project directory, run:
   * Linux/macOS:
     ```bash
     chmod +x scripts/languagelist_updater.sh
     bash scripts/languagelist_updater.sh
     ```
   * Windows:
     ```batch
     scripts\languagelist_updater.bat
     ```

4. **Build GLFW stub:** `./gradlew :jre_lwjgl3glfw:build`

5. **Build the launcher:** `./gradlew :app_pojavlauncher:assembleDebug` (Replace `gradlew` with `gradlew.bat` on Windows).

## Current Status

* [x] OpenJDK 8 Mobile port: ARM32, ARM64, x86, x86_64
* [x] OpenJDK 17 Mobile port: ARM32, ARM64, x86, x86_64
* [x] OpenJDK 21 Mobile port: ARM32, ARM64, x86, x86_64
* [x] Headless mod installer
* [x] Mod installer with GUI
* [x] OpenGL in OpenJDK environment
* [x] OpenAL (works on most devices)
* [x] Support for Minecraft 1.12.2 and below
* [x] Support for Minecraft 1.13 and above
* [x] Support for Minecraft 1.17 (22w13a) and above
* [x] Game surface zooming
* [x] New input pipe rewritten to native code
* [x] Rewritten entire controls system
* [ ] More to come!

## Known Issues

See our [issue tracker](https://github.com/AngelAuraMC/Amethyst-Android/issues) for a list of known issues and their current status.

## FAQ

See our [wiki](https://wiki.angelauramc.dev/) for more information.

## Contributing

Contributions are welcome! We welcome any type of contribution, not only code. For example, you can help improve the [wiki](https://github.com/AngelAuraMC/angelauramc.github.io/), contribute to the [translations](https://crowdin.com/project/pojavlauncher), or submit bug reports and feature requests.

Any code change should be submitted as a pull request. The description should explain what the code does and give steps to execute it.

## Support

For support, please join our [Discord server](https://discord.gg/5ptqkyZxEy).

## License

Amethyst is licensed under [GNU LGPLv3](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/LICENSE).

## Credits & Dependencies

* [Boardwalk](https://github.com/zhuowei/Boardwalk) (JVM Launcher): Unknown License/[Apache License 2.0](https://github.com/zhuowei/Boardwalk/blob/master/LICENSE) or GNU GPLv2.
* [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): [GLGPL](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/LICENSE)
* Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
* [GL4ES](https://github.com/AngelAuraMC/gl4es): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
* [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues): [LGPL-2.1 License](https://github.com/MobileGL-Dev/MobileGlues/blob/dev-es/LICENSE).
* [Krypton Wrapper](https://github.com/BZLZHH/NG-GL4ES): [MIT License](https://github.com/BZLZHH/NG-GL4ES/blob/main/LICENSE)
* [ANGLE](https://chromium.googlesource.com/angle/angle): [All Rights Reserved](app_pojavlauncher/src/main/assets/licenses/ANGLE_LICENSE).
* [OpenJDK](https://github.com/AngelAuraMC/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
* [LWJGL3](https://github.com/AngelAuraMC/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
* [LWJGLX](https://github.com/AngelAuraMC/lwjglx) (LWJGL2 API compatibility layer for LWJGL3): unknown license.
* [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
* [bhook](https://github.com/bytedance/bhook) (Used for exit code trapping): [MIT license](https://github.com/bytedance/bhook/blob/main/LICENSE).
* [libepoxy](https://github.com/anholt/libepoxy): [MIT License](https://github.com/anholt/libepoxy/blob/master/COPYING).
* [virglrenderer](https://github.com/AngelAuraMC/virglrenderer): [MIT License](https://gitlab.freedesktop.org/virgl/virglrenderer/-/blob/master/COPYING).
* [OpenAL-Soft](https://github.com/kcat/openal-soft): [GNU GPLv2](app_pojavlauncher/src/main/assets/licenses/OPENAL-SOFT_GPL2)
  * [oboe](https://github.com/google/oboe): [Apache License 2.0](app_pojavlauncher/src/main/assets/licenses/OBOE_APACHE2).
  * [pfffft](https://bitbucket.org/jpommier/pffft/src/master/): [ARR](app_pojavlauncher/src/main/assets/licenses/PFFFT_LICENSE)
* [SDL3](https://github.com/libsdl-org/SDL): [zlib License](https://github.com/libsdl-org/SDL/blob/main/LICENSE.txt)
* [sdl2-compat](https://github.com/libsdl-org/sdl2-compat): [zlib License](https://github.com/libsdl-org/sdl2-compat/blob/main/LICENSE.txt)
* Thanks to [MCHeads](https://mc-heads.net) for providing Minecraft avatars.

## Roadmap

We are currently focusing on:

* Exploring new rendering technologies.

Future plans include:

* Improving stability and performance.
* Enhancing the mod installation experience.

We welcome community feedback and suggestions for our roadmap.  Please feel free to open a feature request in our [issue tracker](https://github.com/AngelAuraMC/Amethyst-Android/issues).
