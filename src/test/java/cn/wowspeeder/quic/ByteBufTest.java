package cn.wowspeeder.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

public class ByteBufTest {
    public static void main(String[] args) {

       ByteBuf byteBuf = Unpooled.copiedBuffer("Bye\r\n", CharsetUtil.UTF_8);
       byteBuf.release();
       System.out.println(byteBuf.readableBytes());
       byteBuf = Unpooled.buffer();
       System.out.println(byteBuf.readableBytes());

    }
}
