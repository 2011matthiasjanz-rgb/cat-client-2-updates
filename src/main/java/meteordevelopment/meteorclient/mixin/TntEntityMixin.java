/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.IMeteorTntEntity;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.SpearCannon;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Only has a real (server-authoritative) effect when this mod's user is hosting the world
// (integrated server / LAN) - tick() only runs authoritatively on that side.
@Mixin(TntEntity.class)
public abstract class TntEntityMixin extends Entity implements IMeteorTntEntity {
    @Unique
    private boolean meteor$cannonTarget;

    public TntEntityMixin(net.minecraft.entity.EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    public void meteor$markAsCannonTarget() {
        meteor$cannonTarget = true;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void meteor$homeTowardPlayer(CallbackInfo ci) {
        if (this.getEntityWorld().isClient()) return;
        if (!meteor$cannonTarget) return;

        SpearCannon spearCannon = Modules.get().get(SpearCannon.class);
        if (!spearCannon.isActive()) return;

        PlayerEntity nearest = this.getEntityWorld().getClosestPlayer(
            this.getX(), this.getY(), this.getZ(), -1,
            (Entity entity) -> entity instanceof PlayerEntity player && !player.isSpectator() && !player.isCreative()
        );
        if (nearest == null) {
            meteor$cannonTarget = false;
            return;
        }

        Vec3d toTarget = nearest.getEntityPos().subtract(this.getEntityPos());
        double distance = toTarget.length();

        if (distance < 1.0) {
            ((TntEntity) (Object) this).setFuse(0);
            meteor$cannonTarget = false;
            return;
        }

        Vec3d direction = toTarget.normalize();
        double speed = 0.5 * spearCannon.homingStrength();
        this.setVelocity(direction.multiply(speed));
        this.velocityDirty = true;

        if (spearCannon.ignoreWalls()) {
            // Nudge the entity directly along the homing line each tick rather than relying
            // solely on move()'s velocity integration, reducing (not fully eliminating) how
            // much thin obstacles can deflect it - move()'s own block collision still applies.
            this.setPosition(this.getX() + direction.x * 0.1, this.getY() + direction.y * 0.1, this.getZ() + direction.z * 0.1);
        }
    }
}
