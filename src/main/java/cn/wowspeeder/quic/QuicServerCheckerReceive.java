package cn.wowspeeder.quic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class QuicServerCheckerReceive extends SimpleChannelInboundHandler<Object> {
    private static InternalLogger logger = InternalLoggerFactory.getInstance(QuicServerCheckerReceive.class);
    private long startTime = System.currentTimeMillis();
    private int i = 0;

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
//        ctx.channel().pipeline().remove(this);
        if(i < 3){
            logger.info("Channel: {}, Receive {}, readableBytes： {}, time: {} {}", ctx.channel().id(), i,byteBuf.readableBytes(),System.currentTimeMillis()-startTime, System.currentTimeMillis() );
            startTime = System.currentTimeMillis();
            i++;
        }
        ctx.fireChannelRead(byteBuf);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
