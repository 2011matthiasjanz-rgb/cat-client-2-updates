# Credits

## Meteor Client

"Cat Client 2" is a personal, unofficial fork of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client),
created by MeteorDevelopment (MineGame159, squidoodly, seasnail) and contributors. All modules, the
module/settings framework, and the base ClickGUI system are their work, licensed under GPL-3.0
(see [LICENSE](LICENSE)). This fork does not remove or alter that license, and any redistribution
of this fork must comply with its terms (source availability, same license, clear attribution).

Fork-specific changes in this repository:
- Mod display name, description and accent color changed to "Cat Client 2" branding
  (`fabric.mod.json`). The underlying mod id, Java package, and all modules/systems are untouched.
- Two additional ClickGUI themes added (`gui/themes/catsend`, `gui/themes/classic`), reusing
  Meteor's existing pluggable `GuiTheme`/`GuiThemes` system. Selectable in-game via the GUI
  theme dropdown alongside the original "Meteor" theme.
- A nine-slice texture renderer (`renderer/NineSliceTexture.java`, `GuiRenderer.nineSlice()`)
  added to support stretching the sprite textures used by the new themes to arbitrary widget sizes.

## Texture packs

- **"Pixel Cat's End [GUI Only]"** — GUI resource pack used as the basis for the "Pixel Cat's End"
  theme's button/window sprites. Original authorship of this pack is not confirmed by this project;
  if you are the author and want different attribution or removal, please open an issue.
- Vanilla-style GUI sprite set (widget textures matching Minecraft's own `assets/minecraft/textures/gui/sprites/widget`
  layout) — used as the basis for the "Classic" theme.

Both texture sets are used here only as visual skins for Meteor's own ClickGUI widgets (not as
Minecraft resource packs) and are bundled as mod resources under
`src/main/resources/assets/meteor-client/textures/gui/{catsend,classic}/`.
