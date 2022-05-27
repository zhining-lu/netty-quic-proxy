package cn.wowspeeder.quic;

import cn.wowspeeder.encryption.Base64Encrypt;
import cn.wowspeeder.sw.SWCommon;
import cn.wowspeeder.sw.SWServerTcpProxyHandler;
import cn.wowspeeder.websocket.WebSocketClientHandler;
import cn.wowspeeder.websocket.WebSocketLocalFrameHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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

    public QuicLocalProxyHandler(EventLoopGroup workerGroup, QuicSslContext SslContext, String server, Integer port, String password) {
        this.password = password;
        this.SslContext = SslContext;
        this.ssServer = new InetSocketAddress(server, port);
        this.workerGroup = workerGroup;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext clientCtx, ByteBuf msg) throws Exception {
//        long startime = System.currentTimeMillis();
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
            logger.info("URI: GET /" + targetAddrBase64 + "  " + targetAddr.dstAddr() + ":" + targetAddr.dstPort());

            long startTime0 = System.currentTimeMillis();
//            workerGroup = new NioEventLoopGroup(1);
            ChannelHandler codec = new QuicClientCodecBuilder()
                    .sslEngineProvider(q -> SslContext.newEngine(q.alloc(), ssServer.getHostString(), ssServer.getPort()))
                    .maxIdleTimeout(1000 * 60 * 10, TimeUnit.MILLISECONDS)
                    .initialMaxData( 1024 * 1024 * 20) //20M
                    // As we don't want to support remote initiated streams just setup the limit for local initiated
                    // streams in this example.
                    .initialMaxStreamDataBidirectionalLocal( 1024 * 1024 * 20) //2M
                    .initialMaxStreamDataBidirectionalRemote( 1024 * 1024 * 20) //2M
//                    .maxAckDelay(10,TimeUnit.MILLISECONDS)
                    .build();
            logger.info("codec "+ (System.currentTimeMillis() - startTime0));
            proxyClient = new Bootstrap();//

            Channel channel = proxyClient.group(workerGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_RCVBUF, 20 * 1024 * 1024)// 接收缓冲区为20M
                    .option(ChannelOption.SO_SNDBUF, 20 * 1024 * 1024)// 发送缓冲区为20M
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
                    .remoteAddress(new InetSocketAddress(ssServer.getHostString(), ssServer.getPort()));

            quicChannelBootstrap
                    .connect()
                    .addListener(f -> {
                        if (f.isSuccess()) {
                            quicChannel = (QuicChannel) f.get();
                            logger.info("quicChannel {}, time: {}", f.isSuccess(), System.currentTimeMillis()-startTime0);
                            remoteStreamChannel = createStream(quicChannel).sync().get();
                            //write remaining bufs
                            remoteStreamChannel.writeAndFlush(Unpooled.copiedBuffer(URI, CharsetUtil.UTF_8));
                            if (clientBuffs != null) {
                                ListIterator<ByteBuf> bufsIterator = clientBuffs.listIterator();
                                while (bufsIterator.hasNext()) {
                                    remoteStreamChannel.writeAndFlush(bufsIterator.next());
                                }
                                clientBuffs = null;
                            }
                            logger.info("channel {}, connect {}, target {}, time: {} {}", remoteStreamChannel, f.isSuccess(), targetAddr.dstAddr() + ":" + targetAddr.dstPort(), System.currentTimeMillis()-startTime0, System.currentTimeMillis());
                        } else {
                            logger.info("channel {}, connect {}, target {}, cause {}, time: {}", remoteStreamChannel, f.isSuccess(), targetAddr.dstAddr() + ":" + targetAddr.dstPort(), f.cause(), System.currentTimeMillis()-startTime0);
                            proxyChannelClose();
                        }
                    });
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
        logger.info("channel {} is Incative", clientChannel);
        proxyChannelClose();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        proxyChannelClose();
    }

    Future<QuicStreamChannel> createStream(QuicChannel quicChannel) {
        return quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    boolean f = true;
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
//                        clientChannel.writeAndFlush(((ByteBuf) msg).retain());
                        clientChannel.writeAndFlush(msg);
                        if(f){
                            logger.info( "chanel: {}, readableBytes: {}, time: {}" , ctx.channel().id(), ((ByteBuf) msg).readableBytes(), System.currentTimeMillis());
                            f = false;
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
                });
            }
        });
    }


    private void proxyChannelClose() {
//        logger.info("proxyChannelClose");
        try {
            if (clientBuffs != null) {
                clientBuffs.forEach(ReferenceCountUtil::release);
                clientBuffs = null;
            }
            if (remoteStreamChannel != null) {
                remoteStreamChannel.shutdownOutput();
                remoteStreamChannel.close();
                remoteStreamChannel = null;
            }
            if(quicChannel != null){
                quicChannel.close();
            }
            /*if(workerGroup != null){
                workerGroup.shutdownGracefully();
            }*/
            if (clientChannel != null) {
                clientChannel.close();
                clientChannel = null;
            }
            logger.debug("close channel");
        } catch (Exception e) {
            logger.error("close channel error", e);
        }
    }
}
