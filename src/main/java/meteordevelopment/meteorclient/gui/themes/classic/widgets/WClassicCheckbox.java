/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.classic.widgets;

import com.mojang.blaze3d.textures.FilterMode;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.renderer.Texture;

public class WClassicCheckbox extends WCheckbox implements MeteorWidget {
    private static final Texture UNCHECKED = load("checkbox.png");
    private static final Texture UNCHECKED_HOVER = load("checkbox_highlighted.png");
    private static final Texture CHECKED = load("checkbox_selected.png");
    private static final Texture CHECKED_HOVER = load("checkbox_selected_highlighted.png");

    public WClassicCheckbox(boolean checked) {
        super(checked);
    }

    private static Texture load(String name) {
        return Texture.readResource("/assets/meteor-client/textures/gui/classic/" + name, false, FilterMode.NEAREST);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Texture texture = checked
            ? (mouseOver ? CHECKED_HOVER : CHECKED)
            : (mouseOver ? UNCHECKED_HOVER : UNCHECKED);

        renderer.texture(x, y, width, height, 0, texture);
    }
}
