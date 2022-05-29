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

        String URI = "ALHP3KSkLDF3U/9Himn337+V5Gi+d1Bo8b6jXQ9Uj64=";

        Base64Encrypt.getInstance().init("889900");
        Base64Encrypt base64 = Base64Encrypt.getInstance();
        System.out.println(base64.getDesString(URI));
    }
}
