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
package com.liveperson.api;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MessagingConsumer {
    public static final String UMS_SERVICE = "asyncMessagingEnt";
    public static ObjectMapper OM = new ObjectMapper();

    public static String consumerUrl(final Map<String, String> DOMAINS, final String account, final String protocol) {
        return String.format("%s://%s/ws_api/account/%s/messaging/consumer?v=3", protocol, DOMAINS.get(UMS_SERVICE), account);
    }

    public static JsonNode withId(ObjectNode reqWithoutId) {
        return reqWithoutId.put("id", UUID.randomUUID().toString());
    }
    
    public static Predicate<JsonNode> responseFor(ObjectNode req) {
        return m->m.path("reqId").asText().equals(req.path("id").asText());
    }
    
    public static ObjectNode getClock() {
        final ObjectNode getClock = OM.createObjectNode();
        getClock.put("kind", "req")
                .put("type", "GetClock")
                .putObject("body");
        return getClock;
    }

    public static ObjectNode consumerRequestConversation() {
        final ObjectNode consumerRequestConversation = OM.createObjectNode();
        consumerRequestConversation
                .put("type", "cm.ConsumerRequestConversation")
                .putObject("body");
        return consumerRequestConversation;
    }
    public static ObjectNode subscribeMessagingEvents(String dialogId) {
        final ObjectNode subscribeMessagingEvents = OM.createObjectNode();
        subscribeMessagingEvents
                .put("type", "ms.SubscribeMessagingEvents")
                .putObject("body")
                .put("fromSeq", 0)
                .put("dialogId", dialogId);
        return subscribeMessagingEvents;
    }
    public static ObjectNode subscribeExConv() {
        return OM.createObjectNode()
                .put("type", "cqm.SubscribeExConversations");
    }

    public static ObjectNode initConnection(final String consumerJWT) {
        final ObjectNode initConnection = OM.createObjectNode()
                .put("type", "InitConnection");
        initConnection.putObject("body");
        initConnection.putArray("headers")
                .addObject()
                .put("type", ".ams.headers.ConsumerAuthentication")
                .put("jwt", consumerJWT);
        return initConnection;
    }

    public static ObjectNode closeConv3(String convId) {
        final ObjectNode closeConv = OM.createObjectNode();
        closeConv.put("kind", "req")
                .put("type", "cm.UpdateConversationField")
                .putObject("body")
                .put("conversationId", convId)
                .putObject("conversationField")
                .put("field", "ConversationStateField")
                .put("conversationState", "CLOSE");
        return closeConv;
    }

    public static ObjectNode publishText(String convId, String text) {
        final ObjectNode publishText = OM.createObjectNode();
        publishText.put("kind", "req")
                .put("type", "ms.PublishEvent")
                .putObject("body")
                .put("dialogId", convId)
                .putObject("event")
                .put("type", "ContentEvent")
                .put("contentType", "text/plain")
                .put("message", text);
        return publishText;
    }
}
