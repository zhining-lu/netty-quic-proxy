package cn.wowspeeder.quic;

import cn.wowspeeder.encryption.Base64Encrypt;
import cn.wowspeeder.sw.SWCommon;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class QuicLocalProxyHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static InternalLogger logger = InternalLoggerFactory.getInstance(QuicLocalProxyHandler.class);

    private EventLoopGroup workerGroup;
    private EventExecutorGroup eventGroup;
    private InetSocketAddress ssServer;
    private Socks5CommandRequest targetAddr;
    private Channel clientChannel;
    private QuicStreamChannel remoteStreamChannel;
    private QuicChannel quicChannel;
    private Bootstrap proxyClient;
    private String password;
    private List<ByteBuf> clientBuffs;
    private QuicSslContext SslContext;
    private FastThreadLocal<QuicChannel> quicChannelThreadLocal;

    public QuicLocalProxyHandler(EventLoopGroup workerGroup, QuicSslContext SslContext, FastThreadLocal<QuicChannel> quicChannelThreadLocal, String server, Integer port, String password) {
        this.password = password;
        this.SslContext = SslContext;
        this.ssServer = new InetSocketAddress(server, port);
        this.workerGroup = workerGroup;
        this.quicChannelThreadLocal = quicChannelThreadLocal;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext clientCtx, ByteBuf msg) throws Exception {
        if (this.clientChannel == null) {
            this.clientChannel = clientCtx.channel();
            this.targetAddr = clientChannel.attr(SWCommon.REMOTE_DES_SOCKS5).get();
        }
//        if (msg.readableBytes() == 0) return;
        proxy(clientCtx, msg);
//        logger.debug(Thread.currentThread().getName() + "==time: "+(System.currentTimeMillis() - startime)+ ", readableBytes: " + ((ByteBuf) msg).readableBytes());
    }

    private void proxy(ChannelHandlerContext clientCtx, ByteBuf msg) throws Exception {
        logger.debug("channel id {},pc is null {},{}", clientChannel.id().toString(), (remoteStreamChannel == null), msg.readableBytes());
        if (remoteStreamChannel == null && proxyClient == null) {
            Base64Encrypt base64 = Base64Encrypt.getInstance();
            //If the base64 encoding exceeds 76 characters, it will wrap, replace \n or \r in base64 encoding
            String targetAddrBase64 = base64.getEncString(targetAddr.dstAddr() + ":" + targetAddr.dstPort()).replaceAll("\r|\n", "");
            String URI = "GET /" + targetAddrBase64 + "\r\n";
            logger.info("URI: GET /" + targetAddrBase64 + "  ,target: " + targetAddr.dstAddr() + ":" + targetAddr.dstPort());

            QuicChannel qctl = quicChannelThreadLocal.get();

            if(qctl != null ){
                logger.info("quic Channel id: {}, open: {}, writable: {}, timeout: {}, active: {}", qctl.id(), qctl.isOpen(), qctl.isWritable() ,qctl.isTimedOut(), qctl.isActive());
            }
            if(qctl != null && qctl.isActive() ){
                quicChannel = qctl;
            }else{
                quicChannel = createQuicChannel();
                quicChannelThreadLocal.set(quicChannel);
                // Create idleState stream for send and receive heartbeat msg periodically.
                // One quic channel to one idleState stream.
                createIdleStateStream(quicChannel);
            }
            remoteStreamChannel = createStream(quicChannel).sync().getNow();
            remoteStreamChannel.writeAndFlush(Unpooled.copiedBuffer(URI, CharsetUtil.UTF_8));
            //Sleep 2ms repairs the delay of the server accepting the second bytebuf. The cause of the problem is unknown
//            Thread.sleep(2);
            //write remaining bufs
            if (clientBuffs != null) {
                ListIterator<ByteBuf> bufsIterator = clientBuffs.listIterator();
                while (bufsIterator.hasNext()) {
                    ByteBuf byteBuf = bufsIterator.next();
                    remoteStreamChannel.writeAndFlush(byteBuf);
                }
                clientBuffs = null;
            }
        }

        if (remoteStreamChannel == null) {
            if (clientBuffs == null) {
                clientBuffs = new ArrayList<>();
            }
            clientBuffs.add(msg.retain());//
//            logger.debug("channel id {},add to client buff list", clientChannel.id().toString());
        } else {
            if (clientBuffs == null) {
                remoteStreamChannel.writeAndFlush(msg.retain());
            } else {
                clientBuffs.add(msg.retain());//
            }
//            logger.debug("channel id {},remote channel write {}", clientChannel.id().toString(), msg.readableBytes());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        proxyChannelForceClose();
    }
    //rate control
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
//        if(remoteStreamChannel != null){
//            if(!remoteStreamChannel.isWritable()){
//                ctx.channel().config().setAutoRead(false);
//            }
//        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
//        if(ctx.channel().isWritable()){
//            remoteStreamChannel.config().setAutoRead(true);
//        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error(cause);
        cause.printStackTrace();
        proxyChannelForceClose();
    }

    private QuicChannel createQuicChannel() throws InterruptedException, ExecutionException {
        long startTime0 = System.currentTimeMillis();
        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslEngineProvider(q -> SslContext.newEngine(q.alloc(), ssServer.getHostString(), ssServer.getPort()))
                .maxIdleTimeout(QuicCommon.QUIC_PROXY_IDEL_TIME, TimeUnit.SECONDS)
                .initialMaxData(1024 * 1024 * 20) //20M
                .initialMaxStreamDataBidirectionalLocal(1024 * 1024 * 20) //20M
                .initialMaxStreamDataBidirectionalRemote(1024 * 1024 * 20) //20M
//                .maxAckDelay(10, TimeUnit.MILLISECONDS)
                .build();
        logger.info("Codec bulid time: {}", (System.currentTimeMillis() - startTime0));
        proxyClient = new Bootstrap();//

        Channel channel = proxyClient.group(workerGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_RCVBUF, 20 * 1024 * 1024)// 接收缓冲区为20M
                .option(ChannelOption.SO_SNDBUF, 20 * 1024 * 1024)// 发送缓冲区为20M
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024, 2 * 1024 * 1024))// set WRITE_BUFFER_WATER_MARK
                .handler(codec)
                .bind(0).sync().channel();

        QuicChannelBootstrap quicChannelBootstrap = QuicChannel.newBootstrap(channel)
                .streamHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        // As we did not allow any remote initiated streams we will never see this method called.
                        // That said just let us keep it here to demonstrate that this handle would be called
                        // for each remote initiated stream.
                        ctx.close();
                    }
                })
//                .option(QuicChannelOption.QLOG, new QLogConfiguration("./logs/", "QlogTitle", "QlogDesc"))
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024, 2 * 1024 * 1024))// set WRITE_BUFFER_WATER_MARK
                .remoteAddress(new InetSocketAddress(ssServer.getHostString(), ssServer.getPort()));

        QuicChannel quicChannel = quicChannelBootstrap.connect().get();
        logger.info("channel {}, connect server({}) {}, target: {}, time: {} ", quicChannel, (ssServer.getHostString()+":"+ ssServer.getPort()), true, targetAddr.dstAddr() + ":" + targetAddr.dstPort(), System.currentTimeMillis()-startTime0, System.currentTimeMillis());
        return quicChannel;
    }

    Future<QuicStreamChannel> createStream(QuicChannel quicChannel) {
        return quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
//                        clientChannel.writeAndFlush(((ByteBuf) msg).retain());
                        if(clientChannel != null){
                            clientChannel.writeAndFlush(msg);
                        }
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                            // Close the connection once the remote peer did send the FIN for this stream.
//                            ((QuicChannel) ctx.channel().parent()).close();
                            proxyChannelClose();
                        }
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        super.channelInactive(ctx);
                        proxyChannelForceClose();
                    }
                    //rate control
                    @Override
                    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//                        if(clientChannel != null){
//                            if(!clientChannel.isWritable()){
//                                ctx.channel().config().setAutoRead(false);
//                            }
//                        }
                    }

                    @Override
                    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
//                        if(ctx.channel().isWritable()){
//                            clientChannel.config().setAutoRead(true);
//                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                        logger.error(cause);
                        cause.printStackTrace();
                        proxyChannelForceClose();
                    }
                });
            }
        });
    }
    /**
     * This stream sends heartbeat msg periodically
     * to prevent the quic channel from being closed due to timeout.
     */
    Future<QuicStreamChannel> createIdleStateStream(QuicChannel quicChannel) {
        return quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    private ScheduledFuture scheduledFutureTask;
                    private int sendMsgCount = 0;
                    private int RevMsgCount = 0;
                    private int lostCount = 0;

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf byteBuf = (ByteBuf) msg;
                        RevMsgCount ++;
//                        logger.info("Received server heartbeat msg[{}]: {}, quic channel id: {}, stream channel isAutoRead: {}",RevMsgCount, byteBuf.toString(CharsetUtil.UTF_8).replaceAll("\r|\n", ""), ctx.channel().parent().id(), ctx.channel().config().isAutoRead());
                        byteBuf.release();
                    }

                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        //add schedule task for send heartbeat msg to server periodically
                        scheduledFutureTask = ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
                            sendMsgCount ++;
                            lostCount = sendMsgCount - RevMsgCount - 1;
//                            logger.info("Send heartbeat msg[{}] ..., lost: {}, quic channel id: {}, isActive: {}, stream channel isActive: {}, isAutoRead: {}", sendMsgCount, lostCount, ctx.channel().parent().id(), ctx.channel().parent().isActive(), ctx.channel().isActive(), ctx.channel().config().isAutoRead());
                            ctx.channel().writeAndFlush(Unpooled.copiedBuffer("GET /\r\n", CharsetUtil.UTF_8));
                            if(lostCount >= 2){
                                //close the quic channel if lostCount >= 2
                                ctx.channel().parent().close();
                                logger.info("Close quic channel id: {}", ctx.channel().parent().id());
                            }
                            // After sending for a period of time, no heartbeat information will be sent,
                            // and the parent channel will be closed due to timeout
                            if(sendMsgCount >= 60){
                                ctx.channel().close();
                            }
                        }, QuicCommon.QUIC_PROXY_IDEL_TIME / 4 * 3, QuicCommon.QUIC_PROXY_IDEL_TIME / 4 * 3, TimeUnit.SECONDS);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        super.channelInactive(ctx);
                        scheduledFutureTask.cancel(true);
                        ctx.channel().close();
                    }
                });
            }
        });
    }

    private void proxyChannelClose() {
        try {
            synchronized (this){
                if (clientBuffs != null) {
                    clientBuffs.forEach(ReferenceCountUtil::release);
                    clientBuffs = null;
                }
                if (remoteStreamChannel != null) {
                    remoteStreamChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                    remoteStreamChannel = null;
                }
                /*if(quicChannel != null){
                    quicChannel.collectStats().addListener(f -> {
                        if (f.isSuccess()) {
                            logger.info("QuicChannel closed: {}", f.getNow());
                        }
                    });
                    quicChannel.close();
                    quicChannel = null;
                }*/
            /*if(workerGroup != null){
                workerGroup.shutdownGracefully();
            }*/
                if (clientChannel != null) {
                    clientChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                    clientChannel = null;
                }
                logger.debug("close channel");
            }

        } catch (Exception e) {
            logger.error("close channel error", e);
        }
    }
    private void proxyChannelForceClose() {
        try {
            synchronized (this){
                if (clientBuffs != null) {
                    clientBuffs.forEach(ReferenceCountUtil::release);
                    clientBuffs = null;
                }
                if (remoteStreamChannel != null) {
                    remoteStreamChannel.close();
                    remoteStreamChannel = null;
                }
                if (clientChannel != null) {
                    clientChannel.close();
                    clientChannel = null;
                }
                logger.debug("close channel");
            }

        } catch (Exception e) {
            logger.error("close channel error", e);
        }
    }
}
