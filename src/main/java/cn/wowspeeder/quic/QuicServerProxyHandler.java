package cn.wowspeeder.quic;

import cn.wowspeeder.sw.SWCommon;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

public class QuicServerProxyHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(QuicServerProxyHandler.class);

    private QuicStreamChannel quicStreamChannel;
    private Channel remoteChannel;
    private Bootstrap proxyClient;
    private List<ByteBuf> clientBuffs;
    private EventLoopGroup workerGroup;

    public  QuicServerProxyHandler(EventLoopGroup workerGroup){
        this.workerGroup = workerGroup;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        if (this.quicStreamChannel == null) {
            this.quicStreamChannel = (QuicStreamChannel)ctx.channel();
        }
//        logger.debug("channel id {},readableBytes:{}", quicStreamChannel.id().toString(), msg.readableBytes());
        proxy(ctx, msg);
    }

    private void proxy(ChannelHandlerContext ctx, ByteBuf msg) {

        logger.debug("channel id {},pc is null {},{}", quicStreamChannel.id().toString(), (remoteChannel == null), msg.readableBytes());
        if (remoteChannel == null && proxyClient == null) {
            proxyClient = new Bootstrap();//
//            workerGroup = new NioEventLoopGroup();
            InetSocketAddress clientRecipient = quicStreamChannel.attr(QuicCommon.REMOTE_DES).get();

            proxyClient.group(workerGroup).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60 * 1000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
//                    .option(ChannelOption.SO_RCVBUF, 10 * 1024 * 1024)// 读缓冲区为10M
//                    .option(ChannelOption.SO_SNDBUF, 10 * 1024 * 1024)// 发送缓冲区10M
                    .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024, 2 * 1024 * 1024))// set WRITE_BUFFER_WATER_MARK
                    .option(ChannelOption.TCP_NODELAY, false)
                    .option(ChannelOption.SO_LINGER, 10)
                    .handler(
                            new ChannelInitializer<Channel>() {
                                @Override
                                protected void initChannel(Channel ch) throws Exception {
                                    ch.pipeline()
                                            .addLast("timeout", new IdleStateHandler(0, 0, SWCommon.TCP_PROXY_IDEL_TIME, TimeUnit.SECONDS) {
                                                @Override
                                                protected IdleStateEvent newIdleStateEvent(IdleState state, boolean first) {
                                                    logger.debug("{} state:{}", clientRecipient.toString(), state.toString());
                                                    proxyChannelClose();
                                                    return super.newIdleStateEvent(state, first);
                                                }
                                            })
                                            .addLast("quicProxy", new SimpleChannelInboundHandler<ByteBuf>() {
                                                boolean f = true;
                                                @Override
                                                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
//                                                    if(f){
//                                                        logger.info("channel: {}, read remote, readableBytes： {}, time: {}", ctx.channel().id(), msg.readableBytes(), System.currentTimeMillis() );
//                                                        f = false;
//                                                    }
                                                    quicStreamChannel.writeAndFlush(msg.retain());
                                                }
                                                //rate control
                                                @Override
                                                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                                    if(quicStreamChannel != null && !quicStreamChannel.isWritable()){
                                                        ctx.channel().config().setAutoRead(false);
                                                    }
                                                }

                                                @Override
                                                public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                                                    if(ctx.channel().isWritable()){
                                                        quicStreamChannel.config().setAutoRead(true);
                                                    }
                                                }

                                                @Override
                                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
//                                                    logger.debug("channelActive {}",msg.readableBytes());
                                                    super.channelActive(ctx);
                                                }

                                                @Override
                                                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                                    super.channelInactive(ctx);
                                                    proxyChannelClose();
                                                }

                                                @Override
                                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//                                                    super.exceptionCaught(ctx, cause);
                                                    logger.error(cause);
                                                    cause.printStackTrace();
                                                    proxyChannelClose();
                                                }
                                            });
                                }
                            }
                    );
            try {
                long startTime = System.currentTimeMillis();
                proxyClient
                        .connect(clientRecipient)
                        .addListener((ChannelFutureListener) future -> {
                            try {
                                if (future.isSuccess()) {
                                    logger.info("channel id {}, {}<->{}<->{} connect {}, time: {} {}", quicStreamChannel == null ? null:quicStreamChannel.id().toString(), quicStreamChannel == null ? null:quicStreamChannel.remoteAddress().toString(), future.channel().localAddress().toString(), clientRecipient.toString(), future.isSuccess(), System.currentTimeMillis() - startTime, System.currentTimeMillis());
                                    remoteChannel = future.channel();

                                    synchronized (this){
                                        if (clientBuffs != null && quicStreamChannel != null) {
                                            logger.info("clientBuffs: {}, length: {}", clientBuffs, clientBuffs.size());
                                            ListIterator<ByteBuf> bufsIterator = clientBuffs.listIterator();
                                            while (bufsIterator.hasNext()) {
                                                ByteBuf byteBuf = bufsIterator.next();
                                                if(byteBuf.readableBytes() > 0){
                                                    logger.info("channel: {}, write(clientBuffs)：{}, time: {}", ctx.channel().id(), byteBuf.readableBytes(), System.currentTimeMillis() );
                                                    remoteChannel.writeAndFlush(byteBuf);
                                                }
                                            }
                                            clientBuffs = null;
                                        }
                                    }
                                } else {
//                                    logger.error("channel id {}, {}<->{} connect {},cause {}, time: {}", quicStreamChannel.id().toString(), quicStreamChannel.remoteAddress().toString(), clientRecipient.toString(), future.isSuccess(), future.cause(), System.currentTimeMillis() - startTime);
                                    proxyChannelClose();
                                }
                            } catch (Exception e) {
                                logger.error(e);
                                proxyChannelClose();
                            }
                        });
            } catch (Exception e) {
                logger.error("connect internet error", e);
                proxyChannelClose();
                return;
            }
        }

        synchronized (this){
            if (remoteChannel == null) {
                if (clientBuffs == null) {
                    clientBuffs = new ArrayList<>();
                }
                clientBuffs.add(msg.retain());
//            logger.debug("channel id {},add to client buff list", clientChannel.id().toString());
            } else {
                if (clientBuffs == null) {
//                logger.info("channel:{}, write: {}, time: {}",remoteChannel.id() ,msg.readableBytes(), System.currentTimeMillis());
                    remoteChannel.writeAndFlush(msg.retain());
                } else {
                    clientBuffs.add(msg.retain());
                }
//            logger.debug("channel id {},remote channel write {}", clientChannel.id().toString(), msg.readableBytes());
            }
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        proxyChannelClose();
    }
    //rate control
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if(remoteChannel != null){
            if(!remoteChannel.isWritable()){
                ctx.channel().config().setAutoRead(false);
            }
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if(ctx.channel().isWritable()){
            if(remoteChannel != null){
                remoteChannel.config().setAutoRead(true);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
            proxyChannelClose();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause);
        cause.printStackTrace();
        proxyChannelClose();
    }

    private void proxyChannelClose() {
        try {
            synchronized (this){
                if (clientBuffs != null) {
                    clientBuffs.forEach(ReferenceCountUtil::release);
                    clientBuffs = null;
                }
                if (remoteChannel != null) {
                    remoteChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                    remoteChannel = null;
                }
                if (quicStreamChannel != null) {
                    quicStreamChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                    quicStreamChannel = null;
                }
            }

            /*if(workerGroup != null){
                workerGroup.shutdownGracefully();
            }*/

        } catch (Exception e) {
            logger.error("close channel error", e);
        }
    }

}
