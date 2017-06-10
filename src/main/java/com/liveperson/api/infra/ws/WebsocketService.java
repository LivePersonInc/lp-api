package com.liveperson.api.infra.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static com.liveperson.api.infra.ws.TimeoutScheduler.withIn;
import static com.liveperson.utils.GeneralUtils.*;
import java.net.URI;
import java.time.Duration;
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

public class WebsocketService {
private final Logger LOG = LoggerFactory.getLogger(WebsocketService.class);
     public static WebsocketService create(final String uri) {
        HandlerManagerImpl<JsonNode> fm = new HandlerManagerImpl<>();
        return supplyRethrow(()
                -> new WebsocketService(WEB_SOCKET_CONTAINER.connectToServer(new MyIntEP(fm.filter()), null, URI.create(uri)), fm));
    }

    public void send(JsonNode msg) {
        LOG.info("SEND: "+msg);
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
                LOG.info("RECV: "+m);
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

    public WebsocketService(Session ws, HandlerManager<JsonNode> fm) {
        this.ws = ws;
        this.fm = fm;
    }

    public Session getWs() {
        return ws;
    }
    public HandlerManager<JsonNode> getFm() {
        return fm;
    }

    static final ObjectMapper OM = new ObjectMapper();
    protected final Session ws;
    protected final HandlerManager<JsonNode> fm;

}
