/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;

public class SpearReach extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> extraRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("extra-range")
        .description("The distance to add to your spear attack range.")
        .defaultValue(0)
        .min(0)
        .max(1000)
        .sliderMax(10)
        .build()
    );

    public SpearReach() {
        super(Categories.Combat, "spear-reach", "Extends your attack range when holding a spear.", true);
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return theme.label("Note: large values will likely be rejected or cause rubberbanding on any server " +
            "that validates attack range server-side.", Utils.getWindowWidth() / 3.0);
    }

    public double extraRange() {
        return isActive() ? extraRange.get() : 0;
    }
}
