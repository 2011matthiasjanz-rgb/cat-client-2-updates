/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.classic;

import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.classic.widgets.WClassicButton;
import meteordevelopment.meteorclient.gui.themes.classic.widgets.WClassicCheckbox;
import meteordevelopment.meteorclient.gui.themes.classic.widgets.WClassicSlider;
import meteordevelopment.meteorclient.gui.themes.classic.widgets.WClassicWindow;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;

/**
 * GUI theme skinned with the vanilla-style GUI widget sprites (window, button,
 * checkbox and slider textures), giving Meteor's ClickGUI a classic Minecraft look.
 */
public class ClassicGuiTheme extends MeteorGuiTheme {
    public ClassicGuiTheme() {
        super("Classic");
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WClassicWindow(icon, title));
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WClassicButton(text, texture));
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WClassicCheckbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WClassicSlider(value, min, max));
    }
}
