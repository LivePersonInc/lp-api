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
import com.liveperson.api.infra.ws.annotations.WebsocketNotification;
import com.liveperson.api.infra.ws.annotations.WebsocketPath;
import com.liveperson.api.infra.ws.annotations.WebsocketReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.http.Header;

import javax.websocket.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.liveperson.api.infra.ws.TimeoutScheduler.withIn;
import static com.liveperson.utils.GeneralUtils.*;

public final class WebsocketService<U> {
    private final Logger LOG = LoggerFactory.getLogger(WebsocketService.class);
    /**
     * Instantiate Connection based on the URL format given in the WebsocketPath
     * annotation.
     *
     * @param <U> Methods Class Type
     * @param methodsClz
     * @param params
     * @param timeout
     * @return
     */
    public static <U> WebsocketService<U> create(Class<U> methodsClz, Map<String, String> params, int timeout) {
        WebsocketPath websocketPath = methodsClz.getAnnotation(WebsocketPath.class);
        if (websocketPath != null)
            return WebsocketService.create(methodsClz, replaceParams(websocketPath.value(), params), timeout);
        throw new RuntimeException("Missing annotations on class " + methodsClz.getName());
    }

    /**
     * Instantiate Connection based on the domain given in the ServiceName
     * annotation and URL format given in the WebsocketPath annotation.
     *
     * @param <U>
     * @param methodsClz
     * @param params
     * @param domains
     * @param timeout
     * @return
     */
    public static <U> WebsocketService<U> create(Class<U> methodsClz, Map<String, String> params, Map<String, String> domains, int timeout) {
        ServiceName serviceName = methodsClz.getAnnotation(ServiceName.class);
        if (serviceName != null) {
            HashMap<String, String> paramsWithDomain = new HashMap<>(params.size() + 1);
            paramsWithDomain.putAll(params);
            paramsWithDomain.put("domain", domains.get(serviceName.value()));
            return WebsocketService.create(methodsClz, paramsWithDomain, timeout);
        }
        throw new RuntimeException("Missing annotations on class " + methodsClz.getName());
    }

    public static <U> WebsocketService<U> create(Class<U> methodsClz, final String uri, int timeout) {
        HandlerManagerImpl<JsonNode> fm = new HandlerManagerImpl<>();
        return supplyRethrow(()
                -> new WebsocketService(WEB_SOCKET_CONTAINER.connectToServer(new MyIntEP(fm.filter()), null, URI.create(uri)), fm, methodsClz, timeout));
    }

    private final U methods;
    private final String name;

    public void send(JsonNode msg) {
        LOG.debug("{}-{}:SEND: {}", name, getWs().getId(), msg);
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
        final long start = System.nanoTime();
        fm.register(matcher, m -> {
            if (fm.unRegister(matcher) != null) {
                timeout.cancel(true);
                Duration latancy = Duration.ofNanos(System.nanoTime() - start);
                LOG.debug("{}-{}:RECV ({} ms): {}", name, getWs().getId(), latancy.toMillis(), m);
                cf.complete(m);
            }
        });
        return cf;
    }

    public CompletableFuture<JsonNode> request(ObjectNode reqMsg) {
        return request(reqMsg, withIn(Duration.ofSeconds(timeout)));
    }

    public CompletableFuture<JsonNode> request(ObjectNode reqMsg, final CompletableFuture<Void> withIn) {
        final String reqId = UUID.randomUUID().toString();
        send(reqMsg.put("id", reqId));
        return waitForMsg(m -> m.path("reqId").asText().equals(reqId), withIn)
                .exceptionally(t -> {
                    LOG.error("No response from request [{}] {} till timeout received", reqId, reqMsg.get("type").toString(), t);
                    return null;
                });
    }

    public CompletableFuture<JsonNode> request(String type, Optional<JsonNode> body, Optional<ArrayNode> headers, final CompletableFuture<Void> withIn) {
        ObjectNode msg = OM.createObjectNode().put("type", type).put("kind", "req");
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
                                withIn(Duration.ofSeconds(timeout)));
                    }

                    WebsocketNotification websocketNotif = method.getAnnotation(WebsocketNotification.class);
                    if (websocketNotif != null) {
                        Consumer<JsonNode> cb = (Consumer<JsonNode>) args[0];
                        return on(m -> m.path("type").asText().equals(websocketNotif.value()), m -> {
                            LOG.debug("{}:NOTIF {}", name, m);
                            cb.accept(m);
                        });
                    }
                    throw new RuntimeException("Method is not annotated with " + WebsocketReq.class.getName());
                });
    }

    private static final WebSocketContainer WEB_SOCKET_CONTAINER = ContainerProvider.getWebSocketContainer();

    private static class MyIntEP extends Endpoint implements MessageHandler.Whole<String> {
        private final Logger LOG = LoggerFactory.getLogger(MyIntEP.class);
        protected final Predicate<JsonNode> handlers;
        private Session currentSession;
        public MyIntEP(Predicate<JsonNode> filter) {
            this.handlers = filter;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.currentSession = session;
            LOG.debug("Open new Session {} {}",session.getRequestURI(),session.getUserProperties());
            session.addMessageHandler(this);
        }
        @Override
        public void onMessage(String t) {
            LOG.debug("OnMessage {} {}",t,currentSession.getRequestURI(),currentSession.getUserProperties());
            Optional.of(t).map(rethrowF(OM::readTree)).filter(handlers);
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            LOG.debug("onClose called. reason [{}] {} {}", closeReason,session.getRequestURI(), session.getUserProperties());
        }

        @Override
        public void onError(Session session, Throwable thr) {
            if(session != null) {
                LOG.error(String.format("OnError called %s  %s", session.getRequestURI(), session.getUserProperties(), thr));
            }
            else{
                LOG.error("On error called",thr);
            }
        }
    }

    public WebsocketService(Session ws, HandlerManager<JsonNode> fm, Class<U> clz, int timeout) {
        this.ws = ws;
        this.fm = fm;
        this.timeout = timeout;
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
    private final int timeout;

    private static String replaceParams(String template, Map<String, String> params) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            template = template.replaceFirst(String.format("\\{%s\\}", entry.getKey()), entry.getValue());
        }
        return template;
    }
}
