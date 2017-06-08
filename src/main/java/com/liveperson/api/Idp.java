package com.liveperson.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.liveperson.api.infra.ServiceName;
import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.Path;

@ServiceName("idp")
public interface Idp {

    /**
     *
     * @param account
     * @return { "jwt": "" }
     */
    @POST("api/account/{account}/signup")
    Call<JsonNode> signup(@Path("account") String account);
}
