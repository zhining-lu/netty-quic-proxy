package cn.wowspeeder;

import cn.wowspeeder.config.Config;
import cn.wowspeeder.config.ConfigLoader;
import cn.wowspeeder.encryption.Base64Encrypt;
import cn.wowspeeder.quic.*;
import cn.wowspeeder.sw.SWCommon;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.cert.CertificateException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class QuicServer {
    private static InternalLogger logger = InternalLoggerFactory.getInstance(QuicServer.class);

    private static EventLoopGroup bossGroup = Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
    private static final EventLoopGroup workerGroup2 = new NioEventLoopGroup();

    private static final int DATAGRAM_SIZE = 2048;

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

        QuicServerCodecBuilder builder = new QuicServerCodecBuilder().sslContext(sslContext)
                .maxIdleTimeout(SWCommon.TCP_PROXY_IDEL_TIME, TimeUnit.SECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(1024 * 1024 * 20) //20M
                .initialMaxStreamDataBidirectionalLocal(1024 * 1024 * 20)  //20M
                .initialMaxStreamDataBidirectionalRemote(1024 * 1024 * 20) //20M
                .initialMaxStreamsBidirectional(2000 * 1000)
                .initialMaxStreamsUnidirectional(2000 * 1000)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024, 2 * 1024 * 1024))// set WRITE_BUFFER_WATER_MARK
//                .maxAckDelay(10,TimeUnit.MILLISECONDS)
//                .option(QuicChannelOption.QLOG, new QLogConfiguration("./logs/", "QlogTitle", "QlogDesc"))
                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
                .tokenHandler(null)
//                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        super.channelActive(ctx);
                        QuicChannel channel = (QuicChannel) ctx.channel();
                        // Create streams etc..
                        logger.info("QuicChannel {} is active", channel);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        super.channelInactive(ctx);
                        QuicChannel quicChannel = (QuicChannel) ctx.channel();
                        quicChannel.collectStats().addListener(f -> {
                            if (f.isSuccess()) {
                                logger.info("QuicChannel id: {}, closed: {}", quicChannel.id(), f.getNow());
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
                                .addLast(new QuicServerProxyHandler(workerGroup2));
                    }
                });
        if (bossGroup instanceof EpollEventLoopGroup) {
            builder.option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR,
                    EpollQuicUtils.newSegmentedAllocator(20));
        }

        Bootstrap bs = new Bootstrap();
        bs.group(bossGroup)
                .channel(Epoll.isAvailable() ? EpollDatagramChannel.class : NioDatagramChannel.class)
                .option(ChannelOption.SO_RCVBUF, 20 * 1024 * 1024)// 接收缓冲区为20M
                .option(ChannelOption.SO_SNDBUF, 20 * 1024 * 1024)// 发送缓冲区为20M
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024, 2 * 1024 * 1024)); // set WRITE_BUFFER_WATER_MARK

        // Support SO_REUSEPORT feature under linux platform to improve performance
        if (Epoll.isAvailable()) {
            // Use recvmmsg when possible
            bs.option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, DATAGRAM_SIZE)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(DATAGRAM_SIZE * 8))
                    .option(EpollChannelOption.SO_REUSEPORT, true);
            // Use the SO_REUSEPORT feature under the linux system to make multiple threads bind to the same port
            int cpuNum = Runtime.getRuntime().availableProcessors();
            logger.info("using epoll reuseport and cpu: " + cpuNum);
            for (int i = 0; i < cpuNum * 2; i++) {
                ChannelHandler codec = builder.build();
                bs.handler(codec);
                ChannelFuture future = bs.bind(server, port).await();
                if (future.isSuccess()) {
                    logger.info("listen at {}:{}", server, port);
                } else {
                    throw new Exception("bootstrap bind fail port is " + port, future.cause());
                }
            }
        } else {
            ChannelHandler codec = builder.build();
            bs.handler(codec);
            ChannelFuture future = bs.bind(server, port).await();
            if (future.isSuccess()) {
                logger.info("listen at {}:{}", server, port);
            } else {
                throw new Exception("bootstrap bind fail port is " + port, future.cause());
            }
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
