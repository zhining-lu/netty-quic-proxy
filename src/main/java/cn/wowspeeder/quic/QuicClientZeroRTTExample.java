package cn.wowspeeder.quic;

import cn.wowspeeder.encryption.Base64Encrypt;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class QuicClientZeroRTTExample {

    private  String server;
    private  int port;

    public QuicClientZeroRTTExample(String server, int port) {
        this.server = server;
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        QuicClientZeroRTTExample example = new QuicClientZeroRTTExample("192.168.1.131", 1888);
        example.send();
    }

    public void send() throws Exception {

        QuicSslContext SslContext = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).
                applicationProtocols("http/0.9")
                .earlyData(true)
                .build();

        newChannelAndSendData(SslContext, null);
        newChannelAndSendData(SslContext, null);
        /*newChannelAndSendData(SslContext, new EarlyDataSendCallback() {
            @Override
            public void send(QuicChannel quicChannel) {
                createStream(quicChannel).addListener(f -> {
                    if (f.isSuccess()) {
                        QuicStreamChannel streamChannel = (QuicStreamChannel) f.getNow();
                        System.err.println("=====earlydata send====" + System.currentTimeMillis());
                        streamChannel.writeAndFlush(
                                Unpooled.copiedBuffer("GET Early\r\n", CharsetUtil.UTF_8));
                    }
                });
            }
        });*/
    }

    void newChannelAndSendData(QuicSslContext context, EarlyDataSendCallback earlyDataSendCallback) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            long startTime = System.currentTimeMillis();
            ChannelHandler codec = new QuicClientCodecBuilder()
                    .sslEngineProvider(q -> context.newEngine(q.alloc(), this.server, this.port))
                    .maxIdleTimeout(1000 * 60, TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000 * 20)
                    // As we don't want to support remote initiated streams just setup the limit for local initiated
                    // streams in this example.
                    .initialMaxStreamDataBidirectionalLocal(1000000 * 20)
                    .build();
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
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
                    .remoteAddress(new InetSocketAddress(this.server, this.port));

            if (earlyDataSendCallback != null) {
                quicChannelBootstrap.earlyDataSendCallBack(earlyDataSendCallback);
            }

            QuicChannel quicChannel = quicChannelBootstrap
                    .connect()
                    .get();
            System.err.println("===connected ====time: " + (System.currentTimeMillis() - startTime));
            QuicStreamChannel streamChannel = createStream(quicChannel).sync().getNow();
            // Write the data and send the FIN. After this its not possible anymore to write any more data.
            System.err.println("===send other====");
            streamChannel.writeAndFlush(Unpooled.copiedBuffer("GET Other\r\n", CharsetUtil.UTF_8))
                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
            ;

            streamChannel.closeFuture().sync();
            quicChannel.closeFuture().sync();
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    Future<QuicStreamChannel> createStream(QuicChannel quicChannel) {
        return quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf byteBuf = (ByteBuf) msg;
                        System.err.println("=recive==" + byteBuf.toString(CharsetUtil.UTF_8));
                        byteBuf.release();
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                            // Close the connection once the remote peer did send the FIN for this stream.
                            ((QuicChannel) ctx.channel().parent()).close(true, 0,
                                    ctx.alloc().directBuffer(16)
                                            .writeBytes(new byte[]{'k', 't', 'h', 'x', 'b', 'y', 'e'}));
                        }
                    }
                });
            }
        });
    }
}