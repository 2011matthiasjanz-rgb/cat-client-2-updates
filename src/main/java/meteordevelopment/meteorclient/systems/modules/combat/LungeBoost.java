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

public class LungeBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> multiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("multiplier")
        .description("How much the Lunge enchantment's dash impulse is multiplied by.")
        .defaultValue(1.5)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Boolean> verticalBoost = sgGeneral.add(new BoolSetting.Builder()
        .name("vertical-boost")
        .description("Also scales the vertical component of the dash, not just horizontal.")
        .defaultValue(false)
        .build()
    );

    public LungeBoost() {
        super(Categories.Combat, "lunge-boost", "Amplifies the Lunge enchantment's dash on spears.", true);
    }

    public double multiplier() {
        return multiplier.get();
    }

    public boolean verticalBoost() {
        return verticalBoost.get();
    }
}
