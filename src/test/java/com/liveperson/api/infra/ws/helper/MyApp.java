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
package com.liveperson.api.infra.ws.helper;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.websockets.GeneralUtils;
import io.dropwizard.websockets.WebsocketBundle;

import javax.servlet.ServletException;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CompletableFuture;
import org.eclipse.jetty.server.Server;

public class MyApp extends Application<Configuration> {
    private final CompletableFuture<Server> cf;

    public MyApp(CompletableFuture<Server> cf) {
        this.cf = cf;
    }

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        websocketBundle = new WebsocketBundle(AnnotatedEchoServer.class);
        bootstrap.addBundle(websocketBundle);
    }

    private WebsocketBundle websocketBundle;

    @Override
    public void run(Configuration configuration, Environment environment) throws InvalidKeySpecException, NoSuchAlgorithmException, ServletException, DeploymentException {
        environment.lifecycle().addServerLifecycleListener((Server server) -> {
            cf.complete(server);
        });
        
    }

    @Metered
    @Timed
    @ExceptionMetered
    @ServerEndpoint("/annotated-ws")
    public static class AnnotatedEchoServer {
        @OnOpen
        public void myOnOpen(final Session session) throws IOException {
            System.out.println("OPEN");
        }

        @OnMessage
        public void myOnMsg(final Session session, String message) throws IOException {
            JsonNode req = OM.readValue(message, JsonNode.class);
            JsonNode resp = req.path("body").path("resp");
            if (!resp.isMissingNode()) {
                final ObjectNode name = (ObjectNode) resp;
                final JsonNode put = name.put("reqId", req.path("id").asText());
                final String toString = put.toString();
                session.getAsyncRemote().sendText(toString);
            }
        }

        @OnClose
        public void myOnClose(final Session session, CloseReason cr) {
            System.out.println("CLOSE");
        }
    }

    public static ObjectMapper OM = new ObjectMapper();
    public static CompletableFuture<Server> start() throws InterruptedException, IOException {
        CompletableFuture<Server> cf = new CompletableFuture<>();
        Thread serverThread = new Thread(GeneralUtils.rethrow(() -> new MyApp(cf).run(new String[]{"server",Resources.getResource("server.yml").getPath()})));
        serverThread.setDaemon(true);
        serverThread.start();
        return cf;
    }
}
