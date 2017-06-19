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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.liveperson.api.infra.ServiceName;
import static com.liveperson.api.infra.ws.TimeoutScheduler.withIn;
import static com.liveperson.utils.GeneralUtils.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.http.Header;

public final class WebsocketService<U> {
    private final Logger LOG = LoggerFactory.getLogger(WebsocketService.class);
    public static <U> WebsocketService create(final String uri, Class<U> methodsClz) {
        HandlerManagerImpl<JsonNode> fm = new HandlerManagerImpl<>();
        return supplyRethrow(()
                -> new WebsocketService(WEB_SOCKET_CONTAINER.connectToServer(new MyIntEP(fm.filter()), null, URI.create(uri)), fm, methodsClz));
    }
    public static <U> WebsocketService create(final String protocol, final Map<String, String> DOMAINS, final String account, Class<U> methodsClz) {
        ServiceName serviceName = methodsClz.getAnnotation(ServiceName.class);
        if (serviceName != null) 
            return create(protocol, DOMAINS.get(serviceName.value()), account, methodsClz);
        throw new RuntimeException("Missing annotations on class " + methodsClz.getName());
    }

    public static <U> WebsocketService create(final String protocol, final String domain, final String account, Class<U> methodsClz) {
        ServiceName serviceName = methodsClz.getAnnotation(ServiceName.class);
        WebsocketPath websocketPath = methodsClz.getAnnotation(WebsocketPath.class);
        if (serviceName != null && websocketPath != null) 
            return create(String.format(websocketPath.value(), protocol, domain, account), methodsClz);
        throw new RuntimeException("Missing annotations on class " + methodsClz.getName());
    }
    private final U methods;

    public void send(JsonNode msg) {
        LOG.info("SEND: " + msg);
        runRethrow(()
                -> ws.getAsyncRemote().sendText(OM.writeValueAsString(msg)));
    }

    public Predicate<JsonNode> on(Predicate<JsonNode> matcher, Consumer<JsonNode> consumer) {
        return fm.register(matcher, consumer);
    }

    public void off(Predicate<JsonNode> matcher) {
        fm.unRegister(matcher);
    }

    public CompletableFuture<JsonNode> waitForMsg(Predicate<JsonNode> matcher, CompletableFuture<Void> timeout) {
        CompletableFuture<JsonNode> cf = new CompletableFuture<>();
        timeout.thenRun(() -> {
            if (fm.unRegister(matcher) != null)
                cf.completeExceptionally(new TimeoutException());
        });
        fm.register(matcher, m -> {
            if (fm.unRegister(matcher) != null) {
                timeout.cancel(true);
                LOG.info("RECV: " + m);
                cf.complete(m);
            }
        });
        return cf;
    }

    public CompletableFuture<JsonNode> request(ObjectNode reqMsg) {
        return request(reqMsg, withIn(Duration.ofSeconds(3)));
    }

    public CompletableFuture<JsonNode> request(ObjectNode reqMsg, final CompletableFuture<Void> withIn) {
        final String reqId = UUID.randomUUID().toString();
        send(reqMsg.put("id", reqId));
        return waitForMsg(m -> m.path("reqId").asText().equals(reqId), withIn);
    }

    public CompletableFuture<JsonNode> request(String type, Optional<JsonNode> body, Optional<ArrayNode> headers, final CompletableFuture<Void> withIn) {
        ObjectNode msg = OM.createObjectNode().put("type", type);
        body.ifPresent(b -> msg.put("body", b));
        headers.ifPresent(b -> msg.put("headers", b));
        return request(msg, withIn);
    }

    public <T> T caller(Class<T> clz) {
        return (T) Proxy.newProxyInstance(clz.getClassLoader(),
                new Class[]{clz},
                (Object proxy, Method method, Object[] args) -> {
                    WebsocketReq websocketReq = method.getAnnotation(WebsocketReq.class);
                    if (websocketReq != null) {
                        Optional<JsonNode> body = Optional.empty();
                        ArrayNode headers = OM.createArrayNode();
                        Parameter[] parameters = method.getParameters();
                        for (int i = 0; i < parameters.length; i++) {
                            Parameter p = parameters[i];
                            Header header = p.getAnnotation(Header.class);
                            if (header != null) {
                                headers.add(((ObjectNode) args[i]).put("type", header.value()));
                            } else {
                                body = Optional.of((JsonNode) args[i]);
                            }
                        }
                        return request(websocketReq.value(), body,
                                headers.size() == 0 ? Optional.empty() : Optional.of(headers),
                                withIn(Duration.ofSeconds(3)));
                    }
                    WebsocketNotification websocketNofif = method.getAnnotation(WebsocketNotification.class);
                    if (websocketNofif != null) {
//                        Parameter[] parameters = method.getParameters();
//                        for (int i = 0; i < parameters.length; i++) {
//                            Parameter p = parameters[i];
//                            System.out.println(p);
//                            System.out.println(args[i]);  
//                        }
                        Consumer<JsonNode> cb = (Consumer<JsonNode>) args[0];
                        return on(m -> m.path("type").asText().equals(websocketNofif.value()), m -> {
                            LOG.info("{}:NOTIF {}", name, m);
                            cb.accept(m);
                        });
//                        Optional<JsonNode> body = Optional.empty();
//                        ArrayNode headers = OM.createArrayNode();
//                        Parameter[] parameters = method.getParameters();
//                        for (int i = 0; i < parameters.length; i++) {
//                            Parameter p = parameters[i];
//                            Header header = p.getAnnotation(Header.class);
//                            if (header != null) {
//                                headers.add(((ObjectNode) args[i]).put("type", header.value()));
//                            } else {
//                                body = Optional.of((JsonNode) args[i]);
//                            }
//                        }
//                        return request(websocketReq.value(), body,
//                                headers.size() == 0 ? Optional.empty() : Optional.of(headers),
//                                withIn(Duration.ofSeconds(3)));
                    }
                    throw new RuntimeException("Method is not annotated with " + WebsocketReq.class.getName());
                });
    }

    private static final WebSocketContainer WEB_SOCKET_CONTAINER = ContainerProvider.getWebSocketContainer();

    private static class MyIntEP extends Endpoint implements MessageHandler.Whole<String> {
        protected final Predicate<JsonNode> handlers;

        public MyIntEP(Predicate<JsonNode> filter) {
            this.handlers = filter;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(this);
        }

        @Override
        public void onMessage(String t) {
            Optional.of(t).map(rethrowF(OM::readTree)).filter(handlers);
        }
    }

    public WebsocketService(Session ws, HandlerManager<JsonNode> fm, Class<U> clz) {
        this.ws = ws;
        this.fm = fm;
        this.methods = caller(clz);
        this.name = clz.getSimpleName();
    }

    public Session getWs() {
        return ws;
    }
    public U methods() {
        return methods;
    }
    public HandlerManager<JsonNode> getFm() {
        return fm;
    }

    static final ObjectMapper OM = new ObjectMapper();
    protected final Session ws;
    protected final HandlerManager<JsonNode> fm;

}
