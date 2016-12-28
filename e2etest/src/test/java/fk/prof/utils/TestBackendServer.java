package fk.prof.utils;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * @understands running a mock backend-server to support integration-testing recorder
 */
public class TestBackendServer {
    private static class HandlerStub {
        final CompletableFuture<Void> fut;
        private final Function<byte[], byte[]> handler;

        public HandlerStub(Function<byte[], byte[]> handler) {
            this.handler = handler;
            this.fut = new CompletableFuture<Void>();
        }

        public void handle(Request req, OutputStream resStrm) throws IOException {
            ServletInputStream inputStream = req.getInputStream();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            IOUtils.copy(inputStream, os);
            byte[] reqBytes = os.toByteArray();
            byte[] resBytes = handler.apply(reqBytes);

            resStrm.write(resBytes);
            fut.complete(null);
        }
    }

    private final Map<String, Queue<HandlerStub>> actions;
    private Server server;

    public TestBackendServer(final int port) {
        server = new Server(port);
        actions = new ConcurrentHashMap<>();
        Queue<HandlerStub> helloWorldQueue = new ConcurrentLinkedQueue<>();
        actions.put("/hello", helloWorldQueue);
        helloWorldQueue.offer(makeHelloWorldStub(helloWorldQueue));

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{new StubCallingHandler()});
        server.setHandler(handlers);

        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HandlerStub makeHelloWorldStub(Queue<HandlerStub> helloWorldQueue) {
        return new HandlerStub((req) -> {
            helloWorldQueue.offer(makeHelloWorldStub(helloWorldQueue));
            System.out.println("req = [" + new String(req) + "]");
            return "Hello World\n".getBytes(Charset.defaultCharset());
        });
    }

    class StubCallingHandler extends AbstractHandler {
        @Override
        public void handle(String path, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
            Queue<HandlerStub> handlerStubs = actions.get(path);
            HandlerStub stub = handlerStubs.poll();
            ServletOutputStream os = httpServletResponse.getOutputStream();
            stub.handle(request, os);
            os.flush();
        }
    }

    public void stop() {
        try {
            server.stop();
            server.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Future[] register(String path, Function<byte[], byte[]>[] handler) {
        Queue<HandlerStub> handlerStubs = actions.get(path);
        if (handlerStubs == null) {
            handlerStubs = new ConcurrentLinkedQueue<>();
            actions.putIfAbsent(path, handlerStubs);
            handlerStubs = actions.get(path);
        }
        Future[] arr = new Future[handler.length];
        int i = 0;
        for (Function<byte[], byte[]> h : handler) {
            HandlerStub hStub = new HandlerStub(h);
            handlerStubs.offer(hStub);
            arr[i] = hStub.fut;
        }
        return arr;
    }
}
