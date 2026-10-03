/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.registry.tag.ItemTags;

public class SpearSpam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The method of clicking to use.")
        .defaultValue(Mode.Press)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The amount of delay between attacks in ticks.")
        .defaultValue(1)
        .min(0)
        .sliderMax(20)
        .visible(() -> mode.get() == Mode.Press)
        .build()
    );

    private final Setting<Boolean> requireTarget = sgGeneral.add(new BoolSetting.Builder()
        .name("require-target")
        .description("Only spams when an entity is targeted.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> inScreens = sgGeneral.add(new BoolSetting.Builder()
        .name("while-in-screens")
        .description("Whether to spam while a screen is open.")
        .defaultValue(false)
        .build()
    );

    private int timer;

    public SpearSpam() {
        super(Categories.Combat, "spear-spam", "Automatically spams left-click while holding a spear.", true);
    }

    @Override
    public void onActivate() {
        timer = 0;
        mc.options.attackKey.setPressed(false);
    }

    @Override
    public void onDeactivate() {
        mc.options.attackKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!inScreens.get() && mc.currentScreen != null) return;
        if (!mc.player.getMainHandStack().isIn(ItemTags.SPEARS)) return;
        if (requireTarget.get() && mc.targetedEntity == null) return;

        switch (mode.get()) {
            case Hold -> mc.options.attackKey.setPressed(true);
            case Press -> {
                timer++;
                if (timer > delay.get()) {
                    Utils.leftClick();
                    timer = 0;
                }
            }
        }
    }

    public enum Mode {
        Hold,
        Press
    }
}
