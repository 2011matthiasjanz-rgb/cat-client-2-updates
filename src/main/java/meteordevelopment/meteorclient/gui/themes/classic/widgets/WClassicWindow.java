/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.classic.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.renderer.NineSliceTexture;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WClassicWindow extends WWindow implements MeteorWidget {
    private static final NineSliceTexture BODY = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/classic/text_field.png", 1
    );
    private static final NineSliceTexture HEADER = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/classic/button.png", 3
    );

    public WClassicWindow(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new WClassicHeader(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            renderer.nineSlice(x, y + header.height, width, height - header.height, BODY, Color.WHITE);
        }
    }

    private class WClassicHeader extends WHeader {
        public WClassicHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.nineSlice(x, y, width, height, HEADER, Color.WHITE);
        }
    }
}
