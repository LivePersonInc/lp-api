# lp-api
[![Build Status](https://travis-ci.org/LivePersonInc/lp-api.svg?branch=master)](https://travis-ci.org/LivePersonInc/lp-api)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.liveperson.api/lp-api/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.liveperson.api/lp-api)

Consume easily Liveperson apis using this library.

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
    Assert.assertNotNull(body);
    Assert.assertTrue(!body.path("jwt").asText().isEmpty());
```

### User Login

TBD

### Consumer Messaging

```java
    WebsocketService consumer = WebsocketService.create(consumerUrl(domains, LP_ACCOUNT, "wss"));
    JsonNode resp = consumer.request(initConnection(jwt), 
            withIn(Duration.ofSeconds(5))).get();        
    assertThat(resp.path("code").asInt(), is(200));
    consumer.getWs().close();
```