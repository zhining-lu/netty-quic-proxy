package cn.wowspeeder.quic;

import cn.wowspeeder.sw.SWCommon;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;

public class QuicServerCheckerReceive extends SimpleChannelInboundHandler<Object> {
    private static InternalLogger logger = InternalLoggerFactory.getInstance(QuicServerCheckerReceive.class);

    public QuicServerCheckerReceive() {
        super(false);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
//        System.err.println("==QuicServerCheckerReceive===" + byteBuf.toString(CharsetUtil.UTF_8));
//        ctx.channel().pipeline().remove(this);
        long starttime = System.currentTimeMillis();
        ctx.fireChannelRead(msg);
//        logger.info(Thread.currentThread().getName() +"===time: "+ (System.currentTimeMillis()-starttime) + ", readableBytes: " + ((ByteBuf) msg).readableBytes());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
