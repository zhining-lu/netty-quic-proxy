package test;

import cn.wowspeeder.encryption.Base64Encrypt;
import cn.wowspeeder.encryption.ShadowSocksKey;
import cn.wowspeeder.quic.QuicCommon;
import cn.wowspeeder.sw.SWCommon;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Base64EncryptTest {
    public static void main(String[] args) throws Exception {

        String URI = "GET /ALHP3KSkLDF3U/9Himn337+V5Gi+d1Bo8b6jXQ9Uj64=";

        switch (URI) {
            case "GET /":
//                ctx.channel().writeAndFlush(Unpooled.copiedBuffer("Bye\r\n", CharsetUtil.US_ASCII))
//                                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                ;
                System.out.println("case1");
                break;
            default:
                long startime = System.currentTimeMillis();
                System.out.println("case2");
                if (URI.length() < 5) {
//                    ctx.channel().writeAndFlush(Unpooled.copiedBuffer("Bad Request: " + URI, CharsetUtil.US_ASCII))
//                                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                    ;
                    System.out.println("<5");
                    break;
                }
                if (!"GET /".equals(URI.substring(0, 5))) {
//                    ctx.channel().writeAndFlush(Unpooled.copiedBuffer("Bad Request: " + URI, CharsetUtil.US_ASCII))
//                                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                    ;
                    System.out.println("!="+URI.substring(0, 5));
                    break;
                }
                String password = "889900";
                Base64Encrypt.getInstance().init("889900");
                System.err.println("===switch-======");

                System.out.println("timea: " + (System.currentTimeMillis() - startime));
                Base64Encrypt.getInstance().init("889900");
                Base64Encrypt base64 = Base64Encrypt.getInstance();
                //remove "GET /"
                System.out.println("time0: " + (System.currentTimeMillis() - startime));
                String targetHostAndPort = base64.getDesString(URI.substring(5));
                System.err.println("===targetHostAndPort-======" + targetHostAndPort);
                //eg: targetHostAndPort = www.baidu.com:443
                System.out.println("time1: " + (System.currentTimeMillis() - startime));
                Pattern p = Pattern.compile("^\\s*(.*?):(\\d+)\\s*$");
                Matcher m = p.matcher(targetHostAndPort);
                if (m.matches()) {
                    String host = m.group(1);
                    int port = Integer.parseInt(m.group(2));
//                    ctx.channel().attr(QuicCommon.REMOTE_DES).set(new InetSocketAddress(host, port));
                    System.err.println("==========" + host + port);
                    System.out.println("time2: " + (System.currentTimeMillis() - startime));
                } else {
//                    logger.error("TargetAddr format is not rigth: {}", targetHostAndPort);
                    throw new UnsupportedOperationException("TargetAddr format is not rigth: " + targetHostAndPort);
                }
                System.out.println("time: " + (System.currentTimeMillis() - startime));
        }
    }
}
