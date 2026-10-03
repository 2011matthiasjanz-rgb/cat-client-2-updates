/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SpearPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range the entity can be to attack it.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    // Client-side targeting relaxation only. Real spear attacks are still resolved by the
    // server's own collider raycast (PiercingWeaponComponent.stab / AttackRangeComponent) —
    // this setting does not bypass that validation on any server actually enforcing it.
    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("The maximum range the entity can be attacked through walls (client-side targeting only).")
        .defaultValue(8.0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Which entities to attack.")
        .defaultValue(EntityType.PLAYER)
        .onlyAttackable()
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("Order in which targets are prioritised.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();

    public SpearPhase() {
        super(Categories.Combat, "spear-phase", "Relaxes client-side wall targeting checks for spear auto-attacks.", true);
    }

    @Override
    public void onDeactivate() {
        targets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.getMainHandStack().isIn(ItemTags.SPEARS)) return;

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), 1);

        if (targets.isEmpty()) return;

        Entity target = targets.getFirst();
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body));

        if (mc.player.getAttackCooldownProgress(0.5f) >= 1) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if (!entity.isAlive()) return false;
        if (!entities.get().contains(entity.getType())) return false;

        Box hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
            MathHelper.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
            MathHelper.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
            MathHelper.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
            range.get()
        )) return false;

        // See wallsRange field comment above: client-side targeting relaxation only.
        return PlayerUtils.canSeeEntity(entity) || PlayerUtils.isWithin(entity, wallsRange.get());
    }
}
