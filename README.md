# lp-api
[![Build Status](https://travis-ci.org/LivePersonInc/lp-api.svg?branch=master)](https://travis-ci.org/LivePersonInc/lp-api)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.liveperson.api/lp-api/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.liveperson.api/lp-api)

Consume easily Liveperson apis. This library is based on [retrofit](http://square.github.io/retrofit/) for the REST apis, 
and mimics the same concept for websocket based apis.

## Usage

```xml
<dependency>
    <groupId>com.liveperson.api</groupId>
    <artifactId>lp-api</artifactId>
    <version></version>
</dependency>
```
If you use websockets based apis you should have in your runtime dependencies also
```xml
<dependency>
    <groupId>org.eclipse.jetty.websocket</groupId>
    <artifactId>javax-websocket-client-impl</artifactId>
    <version>${jetty.version}</version>
    <scope>runtime</scope>
</dependency>
```

## Domains resolution

```java
domains = GeneralAPI.getDomains(LP_DOMAINS, LP_ACCOUNT);
```

## Services apis

### REST apis

Get instance of the service api endpoint using the ``apiEndpoint`` call.
Here is an example of getting endpoint for the ``IDP`` service.

```java
final Idp apiEndpoint = GeneralAPI.apiEndpoint(domains, Idp.class);
```

### Websocket apis

TBD...

## Liveperson services

### Consumer IDP

```java
    final Idp idp = GeneralAPI.apiEndpoint(domains, Idp.class);
    final JsonNode body = idp
            .signup(LP_ACCOUNT)
            .execute().body();    
    String jwt = body.path("jwt").asText();
```

### User Login

TBD

### Consumer Messaging

The api is defined in the [MessagingConsumer](https://github.com/LivePersonInc/lp-api/blob/master/src/main/java/com/liveperson/api/MessagingConsumer.java) class:

```java
@ServiceName("asyncMessagingEnt")
@WebsocketPath("%s://%s/ws_api/account/%s/messaging/consumer?v=3")
public interface MessagingConsumer {

    @WebsocketReq("GetClock")
    CompletableFuture<JsonNode> getClock();

    @WebsocketReq("cm.ConsumerRequestConversation")
    CompletableFuture<JsonNode> consumerRequestConversation();

    @WebsocketReq("InitConnection")
    CompletableFuture<JsonNode> initConnection(
            @Header(".ams.headers.ConsumerAuthentication") JsonNode jwtHeader);

    @WebsocketReq("cm.UpdateConversationField")
    CompletableFuture<JsonNode> updateConversationField(JsonNode body);

    @WebsocketReq("ms.PublishEvent")
    CompletableFuture<JsonNode> publishEvent(JsonNode body);

    @WebsocketReq("ms.SubscribeMessagingEvents")
    CompletableFuture<JsonNode> subscribeMessagingEvents(JsonNode body);
}
```

You can use this function as follows:

```java
    WebsocketService<MessagingConsumer> consumer = WebsocketService.create("wss", domains, LP_ACCOUNT, MessagingConsumer.class);

    final ObjectNode jwtHeader = OM.createObjectNode().put("jwt", jwt);
    consumer.methods().initConnection(jwtHeader).get();

    String convId = consumer.methods().consumerRequestConversation().get()
            .path("body").path("conversationId").asText();
```

The responses of the methods are CompletableFuture, so you can work async as follows:

```java
    consumer.methods().consumerRequestConversation()
            .thenAccept(resp->{
                String convId = resp.path("body").path("conversationId").asText();
                consumer.methods().publishEvent(publishTextBody(convId, "hello"));
            });
```

You can register handler to handle notifications using the ``on`` method:

```java
    consumer.on(m -> m.path("type").asText().equals("ms.MessagingEventNotification"), x -> {
        System.out.println("NOTIF: " + x);
    });
```