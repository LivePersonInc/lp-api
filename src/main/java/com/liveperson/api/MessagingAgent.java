/**
 * The MIT License
 * Copyright (c) 2018 LivePerson, Inc.
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

import com.fasterxml.jackson.databind.JsonNode;
import com.liveperson.api.AgentMessageTransformer;
import com.liveperson.api.infra.MessageTransformerAnnotation;
import com.liveperson.api.infra.ServiceName;
import com.liveperson.api.infra.ws.WebsocketService;
import com.liveperson.api.infra.ws.annotations.WebsocketNotification;
import com.liveperson.api.infra.ws.annotations.WebsocketPath;
import com.liveperson.api.infra.ws.annotations.WebsocketReq;
import com.liveperson.api.infra.ws.annotations.WebsocketSingleNotification;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

@ServiceName("asyncMessagingEnt")
@MessageTransformerAnnotation(AgentMessageTransformer.class)
@WebsocketPath("{protocol}://{domain}/ws_api/account/{account}/messaging/brand/{bearer}?v=2")
public interface MessagingAgent {
    @WebsocketReq("routing.SubscribeRoutingTasks")
    CompletableFuture<JsonNode> subscribeRoutingTasks();
    @WebsocketReq("routing.SetAgentState")
    CompletableFuture<JsonNode> setAgentState(Map body);

    @WebsocketReq("routing.UpdateRingState")
    CompletableFuture<JsonNode> updateRingState(Map body);

    @WebsocketReq("cqm.SubscribeExConversations")
    CompletableFuture<JsonNode> subscribeExConversations(Map body);

    @WebsocketReq("cm.UpdateConversationField")
    CompletableFuture<JsonNode> updateConversationField(Map body);

    @WebsocketReq("ms.PublishEvent")
    CompletableFuture<JsonNode> publishEvent(Map body);

    @WebsocketSingleNotification("cqm.ExConversationChangeNotification")
    WebsocketService.ListenerBuilder onNextExConversationChangeNotification();

    @WebsocketSingleNotification("ms.MessagingEventNotification")
    WebsocketService.ListenerBuilder onNextMessagingEventNotification();

    @WebsocketNotification("routing.RoutingTaskNotification")
    Predicate<JsonNode> onRoutingTaskNotification(Consumer<JsonNode> cb);
}
