/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.classic.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.renderer.NineSliceTexture;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WClassicSlider extends WSlider implements MeteorWidget {
    private static final NineSliceTexture TRACK = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/classic/slider.png", 1
    );
    private static final NineSliceTexture TRACK_HIGHLIGHTED = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/classic/slider_highlighted.png", 1
    );
    private static final NineSliceTexture HANDLE = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/classic/slider_handle.png", 2, 2, 2, 3
    );
    private static final NineSliceTexture HANDLE_HIGHLIGHTED = NineSliceTexture.get(
        "/assets/meteor-client/textures/gui/classic/slider_handle_highlighted.png", 2, 2, 2, 3
    );

    public WClassicSlider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double valueWidth = valueWidth();

        renderTrack(renderer);
        renderHandle(renderer, valueWidth);
    }

    private void renderTrack(GuiRenderer renderer) {
        NineSliceTexture track = mouseOver ? TRACK_HIGHLIGHTED : TRACK;
        renderer.nineSlice(x, y, width, height, track, Color.WHITE);
    }

    private void renderHandle(GuiRenderer renderer, double valueWidth) {
        double s = handleSize();
        NineSliceTexture handle = (dragging || handleMouseOver) ? HANDLE_HIGHLIGHTED : HANDLE;

        renderer.nineSlice(x + valueWidth, y, s, height, handle, Color.WHITE);
    }
}
