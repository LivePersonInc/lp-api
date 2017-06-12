
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.liveperson.api.Idp;
//import static com.liveperson.api.MessagingConsumer.*;
import java.util.Map;
import java.util.Optional;
import com.liveperson.api.infra.GeneralAPI;
import com.liveperson.api.infra.ws.WebsocketService;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.text.IsEmptyString.*;
import static org.hamcrest.collection.IsCollectionWithSize.*;
import static org.hamcrest.number.OrderingComparison.greaterThan;

import static org.junit.Assert.*;
import com.liveperson.api.MessagingConsumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MessagingTest {
    public static final String LP_ACCOUNT = System.getenv("LP_ACCOUNT");
    public static final String LP_DOMAINS = "https://" + Optional.ofNullable(System.getenv("LP_DOMAINS"))
            .orElse("adminlogin.liveperson.net");
    Map<String, String> domains;
    String jwt;

    @Before
    public void before() throws IOException {
        domains = GeneralAPI.getDomains(LP_DOMAINS, LP_ACCOUNT);
        assertThat(domains.keySet(), hasSize(greaterThan(0)));
        final JsonNode body = GeneralAPI.apiEndpoint(domains, Idp.class)
                .signup(LP_ACCOUNT)
                .execute().body();
        assertThat(body, is(not(nullValue())));
        jwt = body.path("jwt").asText();
        assertThat(jwt, not(isEmptyString()));
    }

    @Test
    public void testUMS() throws Exception {
        CountDownLatch cdl = new CountDownLatch(1);
        WebsocketService<MessagingConsumer> consumer = 
                WebsocketService.create("wss", domains, LP_ACCOUNT, MessagingConsumer.class);

        consumer.methods().initConnection(OM.createObjectNode().put("jwt", jwt)).get();
        String convId = consumer.methods().consumerRequestConversation().get().path("body").path("conversationId").asText();

        consumer.on(m -> m.path("type").asText().equals("ms.MessagingEventNotification"), x -> {
            System.out.println("NOTIF: " + x);
            if (x.findPath("message").asText().equals("hello"))
                cdl.countDown();
        });

        consumer.methods().subscribeMessagingEvents(subscribeMessagingEventsBody(convId)).get();
        Thread.sleep(100);
        consumer.methods().publishEvent(publishTextBody(convId, "hello"));
        final boolean await = cdl.await(3, TimeUnit.SECONDS);
        JsonNode closeResp = consumer.methods().updateConversationField(closeConvBody(convId)).get();
        assertThat(closeResp.path("code").asInt(), is(200));
        consumer.getWs().close();
        assertTrue(await);
    }

    public static ObjectNode closeConvBody(String convId) {
        final ObjectNode body = OM.createObjectNode();
        body.put("conversationId", convId)
                .putObject("conversationField")
                .put("field", "ConversationStateField")
                .put("conversationState", "CLOSE");
        return body;
    }

    public static ObjectNode publishTextBody(String convId, String text) {
        final ObjectNode body = OM.createObjectNode();
        body.put("dialogId", convId)
                .putObject("event")
                .put("type", "ContentEvent")
                .put("contentType", "text/plain")
                .put("message", text);
        return body;
    }

    public static ObjectNode subscribeMessagingEventsBody(String dialogId) {
        final ObjectNode subscribeMessagingEvents = OM.createObjectNode();
        subscribeMessagingEvents
                .put("fromSeq", 0)
                .put("dialogId", dialogId);
        return subscribeMessagingEvents;
    }
    static ObjectMapper OM = new ObjectMapper();
}
