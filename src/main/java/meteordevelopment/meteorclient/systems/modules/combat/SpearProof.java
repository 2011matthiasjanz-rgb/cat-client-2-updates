/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;

import java.util.Set;

// Aim-assist for the spear's charge attack: while charging (holding the use key with a spear),
// rotates toward the best target so releasing the charge connects instead of missing.
public class SpearProof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range the entity can be to aim at it.")
        .defaultValue(6)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to aim at.")
        .onlyAttackable()
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("Order in which targets are prioritised.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private Entity target;

    public SpearProof() {
        super(Categories.Combat, "spear-proof", "Aims your spear's charge attack at the best target so it doesn't miss.", true);
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.getMainHandStack().isIn(ItemTags.SPEARS)) return;
        if (!mc.options.useKey.isPressed()) return;

        target = TargetUtils.get(entity -> {
            if (entity == mc.player || entity == mc.getCameraEntity()) return false;
            if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;
            if (!PlayerUtils.isWithin(entity, range.get())) return false;
            if (!entities.get().contains(entity.getType())) return false;
            if (entity instanceof PlayerEntity player) {
                if (player.isCreative()) return false;
                if (!Friends.get().shouldAttack(player)) return false;
            }
            return true;
        }, priority.get());

        if (target == null) return;

        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body));
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }
}
