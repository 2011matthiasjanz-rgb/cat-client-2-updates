/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.catsend.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.renderer.NineSliceTexture;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WCatsEndWindow extends WWindow implements MeteorWidget {
    private static final NineSliceTexture PANEL = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/catsend/button.png", 20, 4, 20, 4
    );

    public WCatsEndWindow(WWidget icon, String title) {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new WCatsEndHeader(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            renderer.nineSlice(x, y + header.height, width, height - header.height, PANEL, Color.WHITE);
        }
    }

    private class WCatsEndHeader extends WHeader {
        public WCatsEndHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.nineSlice(x, y, width, height, PANEL, Color.WHITE);
        }
    }
}
