/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer;

import com.mojang.blaze3d.textures.FilterMode;

import java.util.HashMap;
import java.util.Map;

/**
 * A texture paired with a fixed pixel border used to stretch it to arbitrary widget
 * sizes (vanilla's "nine_slice" .mcmeta scaling) without distorting the border pixels.
 */
public class NineSliceTexture {
    private static final Map<String, NineSliceTexture> CACHE = new HashMap<>();

    public final Texture texture;
    public final int borderLeft, borderTop, borderRight, borderBottom;

    private NineSliceTexture(Texture texture, int borderLeft, int borderTop, int borderRight, int borderBottom) {
        this.texture = texture;
        this.borderLeft = borderLeft;
        this.borderTop = borderTop;
        this.borderRight = borderRight;
        this.borderBottom = borderBottom;
    }

    public static NineSliceTexture get(String resourcePath, int border) {
        return get(resourcePath, border, border, border, border);
    }

    public static NineSliceTexture get(String resourcePath, int borderLeft, int borderTop, int borderRight, int borderBottom) {
        String key = resourcePath + ":" + borderLeft + "," + borderTop + "," + borderRight + "," + borderBottom;

        return CACHE.computeIfAbsent(key, k -> {
            Texture texture = Texture.readResource(resourcePath, false, FilterMode.NEAREST);
            if (texture == null) throw new IllegalArgumentException("Missing GUI texture: " + resourcePath);

            return new NineSliceTexture(texture, borderLeft, borderTop, borderRight, borderBottom);
        });
    }
}
