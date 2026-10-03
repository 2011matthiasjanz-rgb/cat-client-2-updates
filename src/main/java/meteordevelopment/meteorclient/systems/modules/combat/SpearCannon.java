/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

// Only has a real (server-authoritative) effect when this mod's user is hosting the world
// themselves (integrated server / LAN with friends also running Cat Client 2) - see
// PlayerEntityMixin/TntEntityMixin. No effect on servers not running this mod.
public class SpearCannon extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> homingStrength = sgGeneral.add(new DoubleSetting.Builder()
        .name("homing-strength")
        .description("How strongly the TNT homes toward the target.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 3)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .description("Nudges the TNT directly toward the target each tick instead of only steering its velocity.")
        .defaultValue(true)
        .build()
    );

    public SpearCannon() {
        super(Categories.Combat, "spear-cannon", "When hosting: redirects spear-hit TNT toward the nearest player, exploding on impact. No effect on servers not running this mod.", true);
    }

    public double homingStrength() {
        return homingStrength.get();
    }

    public boolean ignoreWalls() {
        return ignoreWalls.get();
    }
}
