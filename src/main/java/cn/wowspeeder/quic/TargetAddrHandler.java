package cn.wowspeeder.quic;

import cn.wowspeeder.encryption.Base64Encrypt;
import cn.wowspeeder.sw.SWCommon;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TargetAddrHandler extends ChannelInboundHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(QuicServerProxyHandler.class);

    private boolean firstMsg = true;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        // The first message of channel is targetAddr
        if (firstMsg) {
            String URI = byteBuf.toString(CharsetUtil.UTF_8);
            // eg: URI = GET /BASE64ENCODE
            switch (URI) {
                case "GET /":
                    ctx.channel().writeAndFlush(Unpooled.copiedBuffer("Bye\r\n", CharsetUtil.US_ASCII))
//                                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                    ;
                    break;
                default:
                    if (URI.length() < 5) {
                        ctx.channel().writeAndFlush(Unpooled.copiedBuffer("Bad Request: " + URI, CharsetUtil.US_ASCII))
//                                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                        ;
                        break;
                    }
                    if (!"GET /".equals(URI.substring(0, 5))) {
                        ctx.channel().writeAndFlush(Unpooled.copiedBuffer("Bad Request: " + URI, CharsetUtil.US_ASCII))
//                                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                        ;
                        break;
                    }

                    String password = ctx.channel().attr(SWCommon.PASSWORD).get();
                    Base64Encrypt base64 = Base64Encrypt.getInstance();
                    //remove "GET /"
                    String targetHostAndPort = base64.getDesString(URI.substring(5));
                    //eg: targetHostAndPort = www.baidu.com:443
                    Pattern p = Pattern.compile("^\\s*(.*?):(\\d+)\\s*$");
                    Matcher m = p.matcher(targetHostAndPort);
                    if (m.matches()) {
                        String host = m.group(1);
                        int port = Integer.parseInt(m.group(2));
                        ctx.channel().attr(QuicCommon.REMOTE_DES).set(InetSocketAddress.createUnresolved(host, port));
                    } else {
                        logger.error("TargetAddr format is not rigth: {}", targetHostAndPort);
                        throw new UnsupportedOperationException("TargetAddr format is not rigth: " + targetHostAndPort);
                    }
            }
            byteBuf.release();
            //empty bytebuf
            byteBuf = Unpooled.buffer();
            ctx.fireChannelRead(byteBuf);
            firstMsg = false;
        }

        //remove lineDecoder and this handler
        ctx.pipeline().remove(this);
        ctx.pipeline().remove("lineDecoder");

    }
}
