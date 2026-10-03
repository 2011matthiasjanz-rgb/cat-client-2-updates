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
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;

public class SpearMace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> extraHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("additional-height")
        .description("The amount of additional height to spoof. More height means more damage.")
        .defaultValue(0.0)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> onlyWhenFalling = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-falling")
        .description("Only spoofs the smash attack while you're falling, matching vanilla smash-attack requirements.")
        .defaultValue(true)
        .build()
    );

    public SpearMace() {
        super(Categories.Combat, "spear-mace", "Applies Mace-style smash-attack height spoofing to spear attacks.", true);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof IPlayerInteractEntityC2SPacket packet) || packet.meteor$getType() != PlayerInteractEntityC2SPacket.InteractType.ATTACK)
            return;

        if (!mc.player.getMainHandStack().isIn(ItemTags.SPEARS)) return;
        if (mc.player.isGliding()) return;
        if (onlyWhenFalling.get() && mc.player.fallDistance <= 0) return;

        sendPacket(0);
        sendPacket(1.501 + extraHeight.get());
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
