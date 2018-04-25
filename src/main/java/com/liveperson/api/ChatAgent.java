package com.liveperson.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.liveperson.api.infra.ServiceName;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.Map;

@ServiceName("agent")
public interface ChatAgent {

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("/api/account/{account}/agentSession?v=1&NC=true")
    Call<JsonNode>startAgentSession(@Path("account") String account, @Body Map body, @Header("Authorization") String accessToken);

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("/api/account/{accountId}/agentSession/agentSessionId/incomingRequests?v=1&NC=true")
    Call<JsonNode>acceptChat(@Path("account") String account, @Body Map body, @Header("Authorization") String accessToken);

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("/api/account/{accountId}/agentSession/{agentSessionId}/chat/{chatId}/events?v=1&NC=true")
    Call<JsonNode>endChat(@Path("account") String account, @Body Map body, @Header("Authorization") String accessToken);
}
