package test;

import java.net.InetSocketAddress;

public class InetSocketAddressTest {
    public static void main(String[] args) {
        String host = "www.youtube.com";
        int port = 443;
        System.out.println(System.currentTimeMillis());
        InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
        System.out.println(System.currentTimeMillis());
        System.out.println(inetSocketAddress);
        System.out.println("============" + System.currentTimeMillis());
        System.out.println("un==" + InetSocketAddress.createUnresolved(host, port));
        System.out.println("============" + System.currentTimeMillis());
    }
}
