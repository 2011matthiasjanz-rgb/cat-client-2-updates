/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.classic.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.NineSliceTexture;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WClassicButton extends WButton implements MeteorWidget {
    private static final NineSliceTexture NORMAL = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/classic/button.png", 3
    );
    private static final NineSliceTexture HIGHLIGHTED = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/classic/button_highlighted.png", 3
    );

    public WClassicButton(String text, GuiTexture texture) {
        super(text, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        MeteorGuiTheme theme = theme();
        double pad = pad();

        NineSliceTexture sprite = (mouseOver || pressed) ? HIGHLIGHTED : NORMAL;
        renderer.nineSlice(x, y, width, height, sprite, Color.WHITE);

        if (text != null) {
            renderer.text(text, x + width / 2 - textWidth / 2, y + pad, theme.textColor.get(), false);
        }
        else {
            double ts = theme.textHeight();
            renderer.quad(x + width / 2 - ts / 2, y + pad, ts, ts, texture, theme.textColor.get());
        }
    }
}
