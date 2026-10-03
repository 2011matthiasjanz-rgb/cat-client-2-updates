/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;

// NOTE: this cannot force a real server to accept guaranteed one-hit-kill damage — it only
// ensures the client-side preconditions that gate a spear attack's maximum damage bonus are
// always satisfied when the attack packet is sent, the same idiom Criticals already uses for
// crits/smash attacks. Real damage output is still whatever the server computes from that.
public class OneTap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyKillAuraTarget = sgGeneral.add(new BoolSetting.Builder()
        .name("only-killaura-target")
        .description("Only spoofs when attacking Kill Aura's current target.")
        .defaultValue(false)
        .build()
    );

    public OneTap() {
        super(Categories.Combat, "one-tap", "Attempts to make spear attacks always satisfy maximum-damage attack conditions.", true);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof IPlayerInteractEntityC2SPacket packet) || packet.meteor$getType() != PlayerInteractEntityC2SPacket.InteractType.ATTACK)
            return;

        if (!mc.player.getMainHandStack().isIn(ItemTags.SPEARS)) return;
        if (mc.player.isGliding()) return;

        Entity entity = packet.meteor$getEntity();
        if (!(entity instanceof LivingEntity) || (entity != Modules.get().get(KillAura.class).getTarget() && onlyKillAuraTarget.get()))
            return;

        sendPacket(0);
        sendPacket(1.501);
        sendPacket(0);
    }

    private void sendPacket(double height) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(x, y + height, z, false, false);
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(packet);
    }
}
