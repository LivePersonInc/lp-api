package com.liveperson.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.liveperson.api.infra.ServiceName;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

@ServiceName("agentVep")
public interface AgentVep {

    /**
     *
     * @param account
     * @param body { "username" : "", "password" : "" }
     * @return { "bearer": "" }
     */
    @Headers("Cache-Control: no-cache")
    @POST("api/account/{account}/login?v=1.3")
    Call<JsonNode> login(@Path("account") String account, @Body JsonNode body);
}
