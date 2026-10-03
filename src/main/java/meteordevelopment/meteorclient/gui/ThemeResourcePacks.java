/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.PreInit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Registers a built-in (in-jar) resource pack per GUI theme and switches which one is
 * enabled to match the active {@link GuiTheme}, so vanilla screens (HUD, inventory, etc.)
 * get reskinned to match a theme without the user ever seeing/toggling a resource pack
 * themselves.
 */
@Environment(EnvType.CLIENT)
public class ThemeResourcePacks {
    private static final Map<String, String> THEME_TO_PACK_ID = Map.of(
        "Pixel Cat's End", "catsend",
        "Classic", "classic"
    );

    @PreInit
    public static void init() {
        for (String packId : THEME_TO_PACK_ID.values()) {
            ResourceLoader.registerBuiltinPack(
                Identifier.of(MeteorClient.MOD_ID, packId),
                FabricLoader.getInstance().getModContainer(MeteorClient.MOD_ID).orElseThrow(),
                PackActivationType.NORMAL
            );
        }
    }

    /**
     * @param reload Whether to trigger a resource reload if the enabled packs changed.
     *               Should be false during startup (postInit) since Minecraft performs
     *               its own initial resource load right after using whatever is enabled
     *               at that point, and true for any switch made during gameplay.
     */
    public static void apply(String themeName, boolean reload) {
        if (MeteorClient.mc == null) return;

        ResourcePackManager packManager = MeteorClient.mc.getResourcePackManager();
        String targetPackId = THEME_TO_PACK_ID.get(themeName);

        boolean changed = false;

        for (String packId : THEME_TO_PACK_ID.values()) {
            String profileId = MeteorClient.MOD_ID + ":" + packId;
            boolean shouldBeEnabled = packId.equals(targetPackId);
            boolean isEnabled = packManager.getEnabledIds().contains(profileId);

            if (shouldBeEnabled && !isEnabled) changed |= packManager.enable(profileId);
            else if (!shouldBeEnabled && isEnabled) changed |= packManager.disable(profileId);
        }

        if (changed && reload) MeteorClient.mc.reloadResources();
    }
}
