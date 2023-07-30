package cn.wowspeeder.quic;

import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;

public class QuicCommon {
    public static final AttributeKey<InetSocketAddress> REMOTE_DES = AttributeKey.valueOf("quicTargetAddr");
    public static final int QUIC_PROXY_IDEL_TIME = 60;
}
