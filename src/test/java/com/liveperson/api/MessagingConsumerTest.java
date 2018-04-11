package com.liveperson.api;


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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.liveperson.api.infra.GeneralAPI;
import com.liveperson.api.infra.ws.TimeoutScheduler;
import com.liveperson.api.infra.ws.WebsocketService;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import static com.google.common.collect.ImmutableMap.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class MessagingConsumerTest {
    public static final String LP_ACCOUNT = System.getenv("LP_ACCOUNT");
    public static final String LP_DOMAINS = "https://" + Optional.ofNullable(System.getenv("LP_DOMAINS"))
            .orElse("adminlogin.liveperson.net");
    public static final String HELLO = "hello";
    Map<String, String> domains;
    String jwt;

    @Before
    public void before() throws IOException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);

        domains = GeneralAPI.getDomains(LP_DOMAINS, LP_ACCOUNT);
        assertThat(domains.keySet(), hasSize(greaterThan(0)));

        Idp idp = GeneralAPI.apiEndpoint(domains, Idp.class);
        jwt = idp.signup(LP_ACCOUNT)
                .execute().body().path("jwt").asText();
        assertThat(jwt, not(isEmptyString()));
    }

    @Test
    public void testUMS() throws Exception {
        WebsocketService<MessagingConsumer> consumer = WebsocketService.create(MessagingConsumer.class,
                of("protocol", "wss", "account", LP_ACCOUNT), domains,10);

        consumer.methods().initConnection(of("jwt", jwt)).get();

        String convId = consumer.methods().consumerRequestConversation()
                .get().path("body").path("conversationId").asText();

        consumer.methods().subscribeMessagingEvents(of(
                "dialogId",convId,
                "fromSeq",0)).get();

        Thread.sleep(1000);

        consumer.methods().publishEvent(of(
                "dialogId",convId,
                "event",of(
                        "type","ContentEvent",
                        "contentType","text/plain",
                        "message", HELLO
                )));

        consumer.methods().onNextMessagingEventNotification()
                .where(m -> m.findPath("message").asText().equals(HELLO)).listen().get();

        int closeRespCode = consumer.methods().updateConversationField(of(
                "conversationId", convId,
                "conversationField", of(
                        "field", "ConversationStateField",
                        "conversationState", "CLOSE"
                ))).get().path("code").asInt();

        consumer.getWs().close();

        assertThat(closeRespCode, is(200));
    }
}
