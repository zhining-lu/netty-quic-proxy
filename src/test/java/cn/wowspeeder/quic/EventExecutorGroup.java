package cn.wowspeeder.quic;

import io.netty.util.concurrent.DefaultEventExecutorGroup;

import java.util.concurrent.Callable;

public class EventExecutorGroup {

    private static final io.netty.util.concurrent.EventExecutorGroup eventGroup = new DefaultEventExecutorGroup(16);
    public static void main(String[] args) {
        eventGroup.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {

                return "hello";
            }
        }).addListener(f -> {
            if (f.isSuccess()){
                System.out.println(  f.get());
            }
        });


    }
}
