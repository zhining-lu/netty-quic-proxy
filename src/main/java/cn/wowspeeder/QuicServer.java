package cn.wowspeeder;

import cn.wowspeeder.config.Config;
import cn.wowspeeder.config.ConfigLoader;
import cn.wowspeeder.encryption.Base64Encrypt;
import cn.wowspeeder.quic.*;
import cn.wowspeeder.sw.SWCommon;
import cn.wowspeeder.sw.SWServerCheckerReceive;
import cn.wowspeeder.sw.SWServerCheckerSend;
import cn.wowspeeder.sw.SWServerTcpProxyHandler;
import cn.wowspeeder.websocket.WebSocketServerInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class QuicServer {
    private static InternalLogger logger = InternalLoggerFactory.getInstance(QuicServer.class);

    private static EventLoopGroup bossGroup = new NioEventLoopGroup();

    private static QuicServer QuicServer = new QuicServer();

    public static QuicServer getInstance() {
        return QuicServer;
    }

    private QuicServer() {

    }

    public void start(String configPath) throws Exception {
        final Config config = ConfigLoader.load(configPath);
        logger.info("load config !");

        for (Map.Entry<Integer, String> portPassword : config.getPortPassword().entrySet()) {
            startSingle(config.getServer(), portPassword.getKey(), portPassword.getValue());
        }
    }

    private void startSingle(String server, Integer port, String password) throws Exception {
        Base64Encrypt.getInstance().init(password);
        // Configure SSL.
        QuicSslContext sslContext = getSslContext();

        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(sslContext)
                .maxIdleTimeout(1000 * 60, TimeUnit.MILLISECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(1024 * 1024 * 20) //20M
                .initialMaxStreamDataBidirectionalLocal(1024 * 1024 * 20)  //2M
                .initialMaxStreamDataBidirectionalRemote(1024 * 1024 * 20) //2M
                .initialMaxStreamsBidirectional(1)
                .initialMaxStreamsUnidirectional(1)
                .maxAckDelay(10,TimeUnit.MILLISECONDS)

                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
//                .tokenHandler(NoValidationQuicTokenHandler.INSTANCE)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        QuicChannel channel = (QuicChannel) ctx.channel();
                        // Create streams etc..
                        logger.info("QuicChannel {} is active", channel);
                    }

                    public void channelInactive(ChannelHandlerContext ctx) {
                        ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                            if (f.isSuccess()) {
                                logger.info("QuicChannel closed: {}", f.getNow());
                            }
                        });
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.attr(SWCommon.PASSWORD).set(password);
                        // Add a LineBasedFrameDecoder here as we just want to do some simple targerAddr handling.
                        ch.pipeline()
                                .addLast(new QuicServerCheckerReceive())
                                .addLast(new QuicServerCheckerSend())
                                .addLast("lineDecoder", new LineBasedFrameDecoder(1024))
                                .addLast(new TargetAddrHandler())
                                .addLast(new QuicServerProxyHandler());
                    }
                }).build();

        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(bossGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_RCVBUF, 20 * 1024 * 1024)// 接收缓冲区为20M
                    .option(ChannelOption.SO_SNDBUF, 20 * 1024 * 1024)// 发送缓冲区为20M
                    .handler(codec)
                    .bind(server, port).sync().channel();
            logger.info("listen at {}:{}", server, port);
            channel.closeFuture().sync();
        } finally {
            stop();
        }

    }
    private QuicSslContext getSslContext() throws CertificateException {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext sslCtx = QuicSslContextBuilder.forServer(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols("http/0.9")
//                .earlyData(true)
                .build();

        return sslCtx;
    }

    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        logger.info("Stop Server!");
    }


    public static void main(String[] args) throws InterruptedException {
        try {
            getInstance().start("conf/config-example-server.json");
        } catch (Exception e) {
            e.printStackTrace();
            getInstance().stop();
            System.exit(-1);
        }
    }

}
