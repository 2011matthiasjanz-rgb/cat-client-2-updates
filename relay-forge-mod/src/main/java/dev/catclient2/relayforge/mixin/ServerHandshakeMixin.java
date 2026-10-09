package dev.catclient2.relayforge.mixin;

import dev.catclient2.relayforge.ChannelBridge;
import dev.catclient2.relayforge.RelayTokenRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/**
 * Intercepts the very first packet a connecting client sends. Every normal connection (including
 * the server's actual players) falls through untouched - only a hostName carrying one of this mod's
 * own marker prefixes is diverted, before Minecraft's login sequence (and Forge's own FML handshake
 * negotiation) ever starts for that connection. See relay-forge-mod's module doc in the project plan
 * for why this has to happen this early: once vanilla/Forge login begins, the pipeline carries
 * state this mod would otherwise have to fight.
 */
@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class ServerHandshakeMixin {
    private static final String REGISTER_PREFIX = "catclient2-register:";
    private static final String RELAY_PREFIX = "catclient2-relay:";

    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "handleIntention*", at = @At("HEAD"), cancellable = true)
    private void relayforge$onIntention(ClientIntentionPacket packet, CallbackInfo ci) {
        String hostName = packet.getHostName();
        if (hostName == null) return;

        Channel channel = ((ConnectionAccessor) (Object) connection).relayforge$getChannel();

        if (hostName.startsWith(REGISTER_PREFIX)) {
            String token = hostName.substring(REGISTER_PREFIX.length());
            ci.cancel();
            relayforge$registerHost(token, channel);
            return;
        }

        if (hostName.startsWith(RELAY_PREFIX)) {
            String token = hostName.substring(RELAY_PREFIX.length());
            ci.cancel();
            relayforge$bridgeJoiner(token, channel);
        }
    }

    private void relayforge$registerHost(String token, Channel channel) {
        if (!RelayTokenRegistry.INSTANCE.register(token, channel)) {
            channel.close();
            return;
        }
        // Kept open, deliberately not read from again until a joiner claims it - the eventual
        // bridge reuses this exact channel's pipeline, and a handler reading from it now would
        // race that handoff. If the host disconnects first, this listener releases the
        // registration so a stale token cannot be claimed.
        channel.closeFuture().addListener(future -> RelayTokenRegistry.INSTANCE.unregister(token, channel));
        relayforge$stripVanillaHandlers(channel.pipeline());
    }

    private void relayforge$bridgeJoiner(String token, Channel joinerChannel) {
        Channel hostChannel = RelayTokenRegistry.INSTANCE.claim(token);
        if (hostChannel == null) {
            joinerChannel.close();
            return;
        }

        relayforge$stripVanillaHandlers(joinerChannel.pipeline());
        ChannelBridge.link(hostChannel, joinerChannel);
    }

    // Standard handler names Minecraft/Forge install on a fresh server connection, confirmed at
    // runtime against a live 1.20.1/Forge 47.2.0 pipeline (see relay-forge-mod's verification log):
    // [timeout, splitter, decoder, prepender, encoder, unbundler, bundler, packet_handler]. Named
    // explicitly (rather than clearing the whole pipeline) since the sentinel head/tail entries in
    // Netty's own names() list must never be touched.
    private static final String[] VANILLA_HANDLER_NAMES = {
        "timeout", "splitter", "decoder", "prepender", "encoder", "unbundler", "bundler", "legacy_query", "packet_handler"
    };

    /** Removes Minecraft's own length/codec handlers, leaving a raw byte channel. */
    private void relayforge$stripVanillaHandlers(ChannelPipeline pipeline) {
        for (String name : VANILLA_HANDLER_NAMES) {
            if (pipeline.get(name) != null) pipeline.remove(name);
        }
    }
}
