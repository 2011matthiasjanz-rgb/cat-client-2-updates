# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

"Cat Client 2" — a personal, unofficial fork of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client)
(a Fabric mod for Minecraft) for version 1.21.11, plus a custom standalone launcher/installer and a small
"play with friends" service built specifically for this fork. See `CREDITS.md` for exactly what is
fork-specific versus upstream Meteor Client work (mod id, package, and all modules/systems/ClickGUI
framework are untouched upstream code — only branding, two extra GUI themes, and a nine-slice texture
renderer are fork additions on the mod side).

The repository is a multi-module Gradle build with **four** modules:
- Root project (`meteordevelopment.meteorclient`) — the Fabric mod itself.
- `launcher` (`dev.catclient2.launcher`) — a Swing desktop app: Microsoft/Xbox login, downloads
  vanilla Minecraft + Fabric Loader, bundles and installs this mod, launches the game, and hosts
  the friends UI, cosmetics, quick-connect servers, and a self-update system.
- `friends-protocol` (`dev.catclient2.friends.protocol`) — shared DTOs/wire-format constants between
  the launcher and friends-server.
- `friends-server` (`dev.catclient2.friends.server`) — a small `com.sun.net.httpserver.HttpServer`
  based service brokering friend requests and live-session data (never mod jars themselves).

These are genuinely separate Java applications sharing one Gradle build, not layers of one app — the
mod never depends on the launcher or vice versa; only `launcher` depends on `friends-protocol` and
`friends-server` (the launcher can embed a friends-server instance in-process, see
`launcher/.../friends/EmbeddedFriendServer.java`).

## Commands

### Mod (root project)
- Build: `./gradlew build`
- Run a dev client: `./gradlew runClient` (standard Fabric Loom task)
- The `src/launcher/java` source set (compiled with `-Xlint:-options` under Java 8 target) is a
  **separate, legacy** stub (`meteordevelopment.meteorclient.Main`) bundled into the mod jar's own
  manifest as `Main-Class` for people who double-click the raw mod jar — it is unrelated to the
  `launcher` Gradle subproject and should not be confused with it.

### Launcher
- Build the fat jar: `./gradlew :launcher:build` (produces `launcher/build/libs/cat-client-2-launcher.jar`
  plus `run-cat-client-2.bat` from `launcher/dist-template/`, via the `shadowJar`/`copyDistTemplate` tasks)
- Run in dev: `./gradlew :launcher:run`
- Build the Windows installer: `./gradlew :launcher:jpackage` — **requires the WiX Toolset installed
  and on PATH**, and only produces a working `.exe` on Windows (jpackage's `--type exe` shells out to
  WiX `light.exe`/`candle.exe`; this cannot be cross-built from Linux/macOS). This is why
  `.github/workflows/launcher-release.yml` runs on `windows-latest`, separately from the mod's own
  `build.yml` (which runs on `ubuntu-latest` and is unrelated to launcher releases).
- The launcher's own version lives in `gradle/libs.versions.toml` (`versions.launcher`) and is
  templated into `launcher/src/main/resources/launcher.properties` → read at runtime via
  `LauncherConfig.LAUNCHER_VERSION`. Bumping it and pushing a matching `vX.Y.Z` git tag to the
  dedicated `cat-client-2-updates` repo (see below) is how a release is published.

### Friends server
- Build: `./gradlew :friends-server:shadowJar`
- Run standalone: `java -jar friends-server/build/libs/cat-friends-server.jar --port=8765 --data=friends-data.json`

### Release distribution — important asymmetry
This repo's own `origin` git remote tracks **upstream** `MeteorDevelopment/meteor-client`, which is not
writable by this fork's maintainer. Launcher releases are instead published to a **separate, dedicated
repo the maintainer owns**: `2011matthiasjanz-rgb/cat-client-2-updates`, added as a second git remote
(conventionally named `updates`). The release workflow (`.github/workflows/launcher-release.yml`) lives
and runs inside that repo, triggered by pushing a `v*` tag there — it builds `:launcher:jpackage` and
publishes the resulting `.exe` as a GitHub Release asset. `launcher/.../update/UpdateChecker.java` polls
that same repo's `releases/latest` API endpoint (matching any `.exe` asset by extension, not a fixed
filename, since jpackage names it after `--name`/`--app-version` which contain spaces/dots) and compares
versions numerically via `VersionComparator` (so `1.9.0` correctly sorts below `1.10.0`).

## Mod architecture (root project, `src/main/java/meteordevelopment/meteorclient/`)

- **Modules** (the toggleable features): base class `systems/modules/Module.java`. A new module is a
  plain class extending `Module`, registered with one `add(new XModule());` line inside the relevant
  `init*()` method (`initCombat`/`initPlayer`/`initMovement`/`initRender`/`initWorld`/`initMisc`) in the
  `systems/modules/Modules.java` registry singleton (`Modules.get()`). `Module.toggle()` auto-subscribes/
  unsubscribes the module instance to the event bus unless `autoSubscribe = false`, and drives the
  `onActivate()`/`onDeactivate()` lifecycle hooks.
- **Event bus**: Orbit (`meteordevelopment.orbit`), instantiated once as `MeteorClient.EVENT_BUS`.
  Handlers are `@EventHandler`-annotated methods taking one event POJO from `events/*` (sub-packaged by
  domain: `entity`, `game`, `meteor`, `packets`, `render`, `world`). Mixins and systems post events via
  `MeteorClient.EVENT_BUS.post(SomeEvent.get(...))`; many events are cancellable (`event.cancel()`) and
  are the standard decoupling point between a mixin hook and module logic (mixins typically look up
  `Modules.get().get(XModule.class)` directly for simple cases, and post an event instead when several
  modules/systems might care).
- **Mixins**: config at `src/main/resources/meteor-client.mixins.json` (package
  `meteordevelopment.meteorclient.mixin`, plugin `MixinPlugin`, Java 21 compat level, ~200 classes under
  the `client` key). Separate optional mixin sets gate compatibility with other mods when present:
  `meteor-client-{baritone,indigo,lithium,sodium,viafabricplus}.mixins.json`.
- **Persistent systems** (`systems/*`, e.g. `accounts`, `config`, `friends`, `hud`, `macros`, `profiles`,
  `proxies`, `waypoints`, and `modules` itself): each extends abstract `System<T>`, serialized to
  `MeteorClient.FOLDER/<name>.nbt`, looked up via `Systems`/a static `get()` singleton accessor.
- **GUI/ClickGUI theme system**: `gui/GuiTheme.java` is the pluggable abstraction (widget factory +
  per-window persisted layout config); concrete themes live in `gui/themes/{meteor,catsend,classic}`,
  registered in `gui/GuiThemes.java`. "Pixel Cat's End" (`catsend`) and "Classic" (`classic`) are this
  fork's additions, reusing Meteor's own widget-factory pattern — adding a theme means subclassing
  `GuiTheme` and providing `W<Theme>X` widget implementations, not touching the theme-agnostic base
  widgets in `gui/widgets/`. Both fork themes depend on `renderer/NineSliceTexture.java` +
  `GuiRenderer.nineSlice()` (also fork additions) to stretch their sprite textures to arbitrary sizes.
- **Addons**: third-party mod-on-mod extensibility via `addons/AddonManager`/`MeteorAddon` — a module's
  constructor tags which addon (if any) owns it.
- Every source file carries Meteor Client's two-line GPL-3.0 header; preserve it on files that are
  upstream-derived, and see `CREDITS.md`'s disclosure requirements before adding fork-specific files
  that should *not* carry that header (the nine-slice renderer and new theme packages are fork-original).

## Launcher architecture (`launcher/src/main/java/dev/catclient2/launcher/`)

Plain Swing + FlatLaf desktop app, `Launcher.java` is the single orchestrator class wiring every screen's
button listeners (no MVC framework — screens are passive Swing panels exposing getters for their
buttons/fields plus setter methods for display state; `Launcher` owns all the actual logic and async
work). Key packages:
- `auth` — Microsoft/Xbox device-code login via the `net.raphimc:MinecraftAuth` library (same dependency
  the mod jar-in-jars for its own in-game account switching). Note: the device-code consent screen
  currently shows "Minecraft Launcher" (Mojang's own public client ID, `MsaConstants.JAVA_TITLE_ID`) because
  no dedicated Azure AD app registration exists for this project yet — this is cosmetic only, not a bug.
- `install` — `VanillaInstaller`/`FabricInstaller`/`AssetDownloader` download vanilla Minecraft + Fabric
  Loader; `DownloadUtil` is the shared HTTP+SHA1-verified download primitive reused throughout the
  launcher (including by the update system).
- `instance` — `InstanceManager` owns the per-user instance directory layout under
  `OperatingSystem.instanceRoot()` (`%APPDATA%/CatClient2` on Windows); `ModJarProvisioner` unconditionally
  re-copies the bundled `/mod/cat-client-2.jar` resource into the instance's `mods/` on every Play, which
  is why mod updates need no separate mechanism — they ride inside whatever launcher version is installed.
- `launch` — `GameLauncher`/`ArgumentBuilder`/`ClasspathBuilder` build and start the actual Minecraft process.
- `friends` — client side of the friends system (`FriendService`, `FriendApi`, `EmbeddedFriendServer`,
  cosmetics/skin upload via Mojang's own skin API).
- `home` — bundled "what's new" feed (`home-content/news.json`, developer-edited) and the user-editable
  quick-connect server list (`QuickServers`, persisted under the instance root).
- `update` — `UpdateChecker` (polls the dedicated `cat-client-2-updates` repo's latest release) and
  `UpdateInstaller` (downloads the new installer `.exe` and runs it silently with `-q`, a standard WiX
  Burn bundle flag, then exits — the installer's fixed `--win-upgrade-uuid` in the jpackage Gradle task
  makes this an in-place upgrade rather than a side-by-side install). The installer is a **per-user**
  install (`--win-per-user-install`) specifically so this silent self-update needs no UAC elevation.
- `ui`/`ui/theme`/`ui/skin` — Swing screens, the shared `CatClientTheme`/`Icons` (hand-drawn Java2D vector
  icons, not image assets) used for visual consistency across screens, and `SkinPreviewPanel`/
  `SkinRenderer` — a from-scratch software 3D renderer (Java2D only, no OpenGL/JOGL/LWJGL dependency)
  that projects the classic Minecraft player model and texture-maps it per-triangle via affine
  `Graphics2D` transforms, for the drag-to-rotate skin preview on the home/cosmetics screens.

Mod jar bundling is cross-subproject: `launcher/build.gradle.kts`'s `copyModJar` task depends on the
root project's `remapJar` Loom task output (not `jar`/`build`), copied in as a generated resource before
`processResources` runs, so the launcher always embeds a mod jar built from the same source tree.
