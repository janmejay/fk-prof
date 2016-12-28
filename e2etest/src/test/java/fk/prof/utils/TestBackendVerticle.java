package fk.prof.utils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;

/**
 * @understands
 */
public class TestBackendVerticle extends AbstractVerticle {
    @Override
    public void start(Future<Void> startFuture) throws Exception {
        vertx.createHttpServer()
                .requestHandler(req -> {
                    MutableInt sum = new MutableInt(0);
                    Mutable<String> remaining = new MutableObject<String>("");
                    req.handler(b -> {
                        String s = remaining.getValue() + new String(b.getBytes());
                        System.out.println("s = " + s);
                        int newline;
                        int lastIdx = 0;
                        while ((newline = s.indexOf("\n", lastIdx)) > -1) {
                            int i = Integer.parseInt(s.substring(lastIdx, newline));
                            lastIdx = newline + 1;
                            System.out.println("i = " + i);
                            sum.add(i);
                            System.out.println("sum = " + sum.getValue());
                        }
                        remaining.setValue(newline > -1 ? s.substring(newline) : "");
                    }).endHandler(b -> {
                        req.response().end("Sum is: " + sum + "!!!\n");
                    });
                })
                .listen(8080);
    }
}
