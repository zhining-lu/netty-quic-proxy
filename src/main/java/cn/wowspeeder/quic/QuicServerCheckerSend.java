package cn.wowspeeder.quic;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class QuicServerCheckerSend extends ChannelOutboundHandlerAdapter {
    private static InternalLogger logger =  InternalLoggerFactory.getInstance(QuicServerCheckerSend.class);

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx,msg,promise);
    }
}
