package dev.catclient2.relayforge;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Splices two already-accepted Netty channels together: bytes read from one are written straight to
 * the other, in both directions. Installed in place of Minecraft's own codec handlers once a
 * handshake's relay token has been matched, so from that point on the connection carries raw
 * Minecraft-protocol bytes between the joiner and the token's registered host, unparsed.
 */
public final class ChannelBridge {
    private ChannelBridge() {
    }

    public static void link(Channel a, Channel b) {
        a.pipeline().addLast(new Forwarder(b));
        b.pipeline().addLast(new Forwarder(a));
    }

    private static final class Forwarder extends ChannelInboundHandlerAdapter {
        private final Channel destination;

        Forwarder(Channel destination) {
            this.destination = destination;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf) || !destination.isActive()) {
                if (msg instanceof ByteBuf buf) buf.release();
                return;
            }
            destination.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) ctx.close();
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // Half-close only: the other direction on `destination` may still be mid-write, and a
            // full close here would cut that off mid-flight.
            if (destination.isActive()) destination.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
