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
import com.liveperson.api.infra.ServiceName;
import retrofit2.Call;
import retrofit2.http.*;

@ServiceName("msgHist")
public interface MessagingHistory {

    /**
     *
     * @param authorization - conversation jwt , ex : "Bearer {jwt}"
     * @param account - account id
     * @param id - conversation id
     * @return - conversation history json
     */
    @GET("messaging_history/api/account/{account}/conversations/conversation/content/search")
    Call<JsonNode> getConversationContent(@Header("Authorization") String authorization,
                                          @Path("account") String account,
                                          @Query("conversationId") String id);

    /**
     *
     * @param authorization
     * @param account
     * @param state
     * @return
     */
    @GET("messaging_history/api/account/{account}/conversations/conversation/consumer/metadata/search")
    Call<JsonNode> getConversationsMetadata(@Header("Authorization") String authorization,
                                            @Path("account") String account,
                                            @Query("state") String state);
}
