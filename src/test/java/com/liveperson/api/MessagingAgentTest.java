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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.liveperson.api.infra.GeneralAPI;
import com.liveperson.api.infra.ws.WebsocketService;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import sun.jvm.hotspot.runtime.Thread;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.google.common.collect.ImmutableMap.of;
import static com.liveperson.api.AgentMessageTransformer.*;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assert.assertThat;

public class MessagingAgentTest {
    public static final String LP_ACCOUNT = System.getenv("LP_ACCOUNT");
    public static final String LP_USER = System.getenv("LP_USER");
    public static final String LP_PASS = System.getenv("LP_PASS");
    public static final String LP_DOMAINS = "https://" + Optional.ofNullable(System.getenv("LP_DOMAINS"))
            .orElse("adminlogin.liveperson.net");
    public static final Map<String, String> DOMAINS = GeneralAPI.getDomains(LP_DOMAINS, LP_ACCOUNT);
    public static final String HELLO = "HELLO";
    public static final String AGENT_HELLO = "GOODBYE";
    private static String consumerJwt;
    private static String agentOldId;
    private static String agentBearer;
    private static String agentId;

    @BeforeClass
    public static void before() throws IOException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
        ((Logger) LoggerFactory.getLogger(WebsocketService.class)).setLevel(Level.DEBUG);

        Idp idp = GeneralAPI.apiEndpoint(DOMAINS, Idp.class);
        consumerJwt = idp.signup(LP_ACCOUNT)
                .execute().body().path("jwt").asText();
        assertThat(consumerJwt, not(isEmptyString()));

        AgentVep agentVep = GeneralAPI.apiEndpoint(DOMAINS, AgentVep.class);

        JsonNode agentLoginInfo = agentVep
                .login(LP_ACCOUNT, of(
                        "username", LP_USER,
                        "password", LP_PASS))
                .execute().body();
        Assert.assertNotNull("Agent Login Failed", agentLoginInfo);

        agentBearer = agentLoginInfo.path("bearer").asText();
        agentOldId = String.format("%s.%s", LP_ACCOUNT, agentLoginInfo.path("config").path("userId").asText());
        agentId = agentLoginInfo.path("config").path("userPid").asText();
    }

    @Test
    public void testAgent() throws Exception {
        WebsocketService<MessagingAgent> agent = WebsocketService.create(MessagingAgent.class, ImmutableMap.<String, String>builder()
                        .put("protocol", "wss")
                        .put(ACCOUNT, LP_ACCOUNT)
                        .put("bearer", agentBearer)
                        .put(AGENT_ID, agentId)
                        .put(AGENT_OLD_ID, agentOldId).build()
                , DOMAINS, 10);

        agent.methods().setAgentState(of(
                "availability","ONLINE"));

        agent.methods().onRoutingTaskNotification(acceptWaitingRings(agent));

        agent.methods().subscribeRoutingTasks();

        WebsocketService<MessagingConsumer> consumer = WebsocketService.create(MessagingConsumer.class,
                of("protocol", "wss", "account", LP_ACCOUNT), DOMAINS,10);

        consumer.methods().initConnection(of("jwt", consumerJwt)).get();

        String convId = consumer.methods().consumerRequestConversation()
                .get().path("body").path("conversationId").asText();

        agent.methods().subscribeExConversations(of(
                "agentIds", asList(agentId),
                "convState",asList("OPEN")));

        agent.methods().onNextExConversationChangeNotification()
                .where(msg->msg.findPath("convId").asText().equals(convId))
                .listen().get();


        // consumer send message
        consumer.methods().publishEvent(of(
                "dialogId",convId,
                "event",of(
                        "type","ContentEvent",
                        "contentType","text/plain",
                        "message", HELLO
                )));

        // agent verify message
        agent.methods().onNextMessagingEventNotification()
                .where(msg->msg.findPath("message").asText().equals(HELLO)).listen().get();

        // agent send message
       agent.methods().publishEvent(of(
                "dialogId",convId,
                "event",of(
                        "type","ContentEvent",
                        "contentType","text/plain",
                        "message", AGENT_HELLO
                )));

        // consumer subscribe and verify agent message
        consumer.methods().subscribeMessagingEvents(of(
                "dialogId",convId,
                "fromSeq",0));

        consumer.methods().onNextMessagingEventNotification()
                .where(m -> m.findPath("message").asText().equals(AGENT_HELLO)).listen().get();

        // consumer subscribe to conversation metadata changes
        consumer.methods().subscribeExConversationEvents(of(
                "minLastUpdatedTime",0,
                "convState",asList("OPEN","CLOSE")
        ));

        // consumer add notification listener
        CompletableFuture<JsonNode> notification = consumer.methods().onNextExConversationChangeNotification().listen();

        // agent set TTR
        Assert.assertTrue("POST: update TTR failed", agent.methods().updateConversationField(of(
                "conversationId", convId,
                "conversationField", of(
                        "field", "TTRField",
                        "ttrType", "CUSTOM",
                        "value", 1800
                ))).get().path("code").asText().equals("200"));

        // ttr conversation metadata validation
        JsonNode notificationResp = notification.get();

        Assert.assertTrue(notificationResp.path("body").path("changes").get(0).
                path("result").path("conversationDetails").
                path("ttr").get("value").asText().equals(String.valueOf(1800)));

        Assert.assertTrue(notificationResp.path("body").path("changes").get(0).
                path("result").path("conversationDetails").
                path("ttr").get("ttrType").asText().equals("CUSTOM"));


        // agent close conversation
        Assert.assertTrue("POST: agent close conversation",agent.methods().updateConversationField(of(
                "conversationId", convId,
                "conversationField", of(
                        "field", "ConversationStateField",
                        "conversationState", "CLOSE"
                ))).get().path("code").asText().equals("200"));

        // consumer update csat survey
        Assert.assertTrue("POST: consumer update csat failed", consumer.methods().updateConversationField(of(
                "conversationId", convId,
                "conversationField", of(
                        "field", "CSATRate",
                        "csatRate", 5,
                        "csatResolutionConfirmation",true,
                        "status","FILLED"
                ))).get().path("code").asText().equals("200"));

        // agent workspace validate csat ?

        consumer.getWs().close();
        agent.getWs().close();
    }

    private Consumer<JsonNode> acceptWaitingRings(WebsocketService<MessagingAgent> agent) {
        return m -> m.path("body").path("changes").elements().forEachRemaining(c->{
            if (c.path("type").asText().equals("UPSERT")) {
                c.path("result").path("ringsDetails").elements().forEachRemaining(r->{
                    if (r.path("ringState").asText().equals("WAITING")) {
                        agent.methods().updateRingState(of(
                                "ringId",r.path("ringId").asText(),
                                "ringState", "ACCEPTED"
                        ));
                    }
                });
            }
        });
    }
}
