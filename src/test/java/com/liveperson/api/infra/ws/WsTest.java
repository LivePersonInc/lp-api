/**
 * The MIT License
 * Copyright (c) 2017 LivePerson, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.liveperson.api.infra.ws;

import com.liveperson.api.infra.ws.annotations.WebsocketPath;
import com.liveperson.api.infra.ws.annotations.WebsocketReq;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static com.google.common.collect.ImmutableMap.of;
import com.google.common.util.concurrent.RateLimiter;
import java.io.IOException;
import org.junit.Test;
import com.liveperson.api.infra.ws.helper.MyApp;
import static io.dropwizard.util.Duration.seconds;
import static java.lang.System.nanoTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.slf4j.LoggerFactory;

@Category(SlowTests.class)
public class WsTest {
    private static Server server;

    @BeforeClass
    public static void before() throws IOException, InterruptedException, ExecutionException {
        server = MyApp.start().get();
        ((Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    }

    @AfterClass
    public static void after() throws IOException, InterruptedException, ExecutionException, Exception {
        server.stop();
    }

    @Test
    public void testFastRequests() throws Exception {
        WebsocketService<TestMethods> connection = WebsocketService.create(TestMethods.class,
                of("domain", "localhost:48080"));

        Phaser phaser = new Phaser(1); //register also the managing thread.
        RateLimiter rl = RateLimiter.create(100);
        MetricRegistry metrics = new MetricRegistry();
        long end = seconds(3).toNanoseconds() + nanoTime();
        while (nanoTime() < end) {
            phaser.register();
            rl.acquire();
            metrics.meter("send").mark();
            connection.methods().myRequest(createBody())
                    .whenComplete((resp, exp) -> {
                        phaser.arrive();
                        if (exp != null)
                            metrics.meter("exp:" + exp.getClass().getSimpleName()).mark();
                        if (resp != null)
                            metrics.meter("recv").mark();
                    });
        }
        phaser.arriveAndDeregister(); // deregister the managing thread
        phaser.awaitAdvanceInterruptibly(0, 10, SECONDS); // wait for the tasks
        metrics.getMeters().entrySet()
                .forEach(e -> System.out.printf("%s:%d-%f\n", e.getKey(), e.getValue().getCount(), e.getValue().getMeanRate()));
        connection.getWs().close();
    }

    @Test
    public void testMultipleConnections() throws Exception {
        final int CONNECTION_RATE = 50;
        final int CONNECTIONS_NUM = 100;

        ExecutorService es = Executors.newFixedThreadPool(10);
        final BlockingQueue<WebsocketService<TestMethods>> q = new LinkedBlockingQueue<>();

        RateLimiter rlConnect = RateLimiter.create(CONNECTION_RATE);
        for (int i = 0; i < CONNECTIONS_NUM; i++) {
            rlConnect.acquire();
            es.execute(() -> {
                q.add(WebsocketService.create(TestMethods.class,
                        of("domain", "localhost:48080")));
            });
        }

        Phaser phaser = new Phaser(1); //register also the managing thread.
        RateLimiter rl = RateLimiter.create(100);
        MetricRegistry metrics = new MetricRegistry();
        long end = seconds(3).toNanoseconds() + nanoTime();
        while (nanoTime() < end) {
            WebsocketService<TestMethods> connection = q.poll(3, SECONDS);
            phaser.register();
            rl.acquire();
            metrics.meter("send").mark();
            connection.methods().myRequest(createBody())
                    .whenComplete((resp, exp) -> {
                        phaser.arrive();
                        if (exp != null)
                            metrics.meter("exp:" + exp.getClass().getSimpleName()).mark();
                        if (resp != null)
                            metrics.meter("recv").mark();
                    });
            q.add(connection);
        }
        phaser.arriveAndDeregister(); // deregister the managing thread
        phaser.awaitAdvanceInterruptibly(0, 10, SECONDS); // wait for the tasks
        metrics.getMeters().entrySet()
                .forEach(e -> System.out.printf("%s:%d-%f\n", e.getKey(), e.getValue().getCount(), e.getValue().getMeanRate()));

    }

    @WebsocketPath("ws://{domain}/annotated-ws")
    public interface TestMethods {

        @WebsocketReq("MyRequest")
        CompletableFuture<JsonNode> myRequest(JsonNode body);
    }

    public static ObjectNode createBody() {
        final ObjectNode body = OM.createObjectNode();
        body.putObject("resp").put("type", "genericResponse");
        return body;
    }

    static ObjectMapper OM = new ObjectMapper();
}
