/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.catsend;

import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.catsend.widgets.WCatsEndButton;
import meteordevelopment.meteorclient.gui.themes.catsend.widgets.WCatsEndWindow;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

/**
 * GUI theme skinned with the "Pixel Cat's End" resource pack's widget sprites.
 * Inherits all of Meteor's default behaviour/layout and only overrides the painted
 * widgets that have a matching texture in the pack (window, button, checkbox, slider).
 */
public class CatsEndGuiTheme extends MeteorGuiTheme {
    public CatsEndGuiTheme() {
        super("Pixel Cat's End");
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WCatsEndWindow(icon, title));
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WCatsEndButton(text, texture));
    }
}
