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

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static com.google.common.collect.ImmutableMap.of;
import com.google.common.util.concurrent.RateLimiter;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import com.liveperson.api.infra.ws.helper.MyApp;
import io.dropwizard.util.Duration;
import static io.dropwizard.util.Duration.seconds;
import static java.lang.System.nanoTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Phaser;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;

@Category(SlowTests.class)
public class WsTest {
    private static Server server;

    @BeforeClass
    public static void before() throws IOException, InterruptedException, ExecutionException {
        server = MyApp.start().get();
    }

    @AfterClass
    public static void after() throws IOException, InterruptedException, ExecutionException, Exception {
        server.stop();
    }

    @Test
    public void testAgent() throws Exception {
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
            connection.methods().generic(createBody())
                    .whenComplete((resp, exp) -> {
                        phaser.arrive();
                        if (exp != null)
                            metrics.meter("exp:" + exp.getClass().getSimpleName()).mark();
                        if (resp != null)
                            metrics.meter("recv").mark();
                    });
        }
        phaser.arriveAndDeregister(); // deregister the manger thread
        phaser.awaitAdvanceInterruptibly(0, 10, SECONDS); // wait for the tasks
        metrics.getMeters().entrySet()
                .forEach(e -> System.out.printf("%s:%d-%f\n", e.getKey(), e.getValue().getCount(), e.getValue().getMeanRate()));
        connection.getWs().close();
    }
    public static ObjectNode createBody() {
        final ObjectNode body = OM.createObjectNode();
        body.putObject("resp").put("type", "genericResponse");
        return body;
    }

    static ObjectMapper OM = new ObjectMapper();

    @WebsocketPath("ws://{domain}/annotated-ws")
    public interface TestMethods {

        @WebsocketReq("generic")
        CompletableFuture<JsonNode> generic(JsonNode body);

    }
}
