/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.LungeBoost;
import net.minecraft.enchantment.EnchantmentEffectContext;
import net.minecraft.enchantment.effect.entity.ApplyImpulseEnchantmentEffect;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Amplifies the vanilla Lunge enchantment's dash impulse on spears. ApplyImpulseEnchantmentEffect
// is a generic effect used by any enchantment configured with "apply_impulse" (Lunge being the
// spear-relevant one), so scoping this to context.stack() being a spear is both correct and
// sufficient without needing the enchantment id itself.
@Mixin(ApplyImpulseEnchantmentEffect.class)
public class ApplyImpulseEnchantmentEffectMixin {
    @ModifyExpressionValue(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;multiply(D)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d meteor$boostLunge(Vec3d impulse, @Local(argsOnly = true) EnchantmentEffectContext context, @Local(argsOnly = true) Entity user) {
        if (user != MeteorClient.mc.player) return impulse;
        if (!context.stack().isIn(ItemTags.SPEARS)) return impulse;

        LungeBoost lungeBoost = Modules.get().get(LungeBoost.class);
        if (!lungeBoost.isActive()) return impulse;

        double multiplier = lungeBoost.multiplier();
        double x = impulse.x * multiplier;
        double y = lungeBoost.verticalBoost() ? impulse.y * multiplier : impulse.y;
        double z = impulse.z * multiplier;

        return new Vec3d(x, y, z);
    }
}
