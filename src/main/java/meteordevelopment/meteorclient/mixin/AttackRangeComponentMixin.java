/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.Hitboxes;
import meteordevelopment.meteorclient.systems.modules.combat.SpearReach;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.ToDoubleFunction;

@Mixin(AttackRangeComponent.class)
public class AttackRangeComponentMixin {
    @ModifyExpressionValue(method = "isWithinRange(Lnet/minecraft/entity/LivingEntity;Ljava/util/function/ToDoubleFunction;D)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/component/type/AttackRangeComponent;hitboxMargin:F", opcode = Opcodes.GETFIELD))
    private float modifyHitboxMargin(float original, LivingEntity entity, ToDoubleFunction<Vec3d> squaredDistanceFunction, double extraHitboxMargin) {
        float v = (float) Modules.get().get(Hitboxes.class).getEntityValue(entity);
        return original + v;
    }

    @ModifyReturnValue(method = "getEffectiveMaxRange", at = @At("RETURN"))
    private float modifySpearMaxRange(float original, Entity entity) {
        if (entity != MeteorClient.mc.player) return original;
        if (!MeteorClient.mc.player.getMainHandStack().isIn(ItemTags.SPEARS)) return original;

        return original + (float) Modules.get().get(SpearReach.class).extraRange();
    }
}
