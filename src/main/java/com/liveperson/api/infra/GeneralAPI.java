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
package com.liveperson.api.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liveperson.api.infra.ws.WebsocketPath;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class GeneralAPI {
    public interface CSDS {
        @GET("csdr/account/{account}/service/baseURI.json?version=1.0")
        Call<JsonNode> baseURI(@Path("account") String account);
    }

    public static final Stream<JsonNode> iteratorToStream(final Iterator<JsonNode> elements) {
        Iterable<JsonNode> iterable = () -> elements;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static Map<String, String> getDomains(final String LP_DOMAINS, final String account) {
        try {
            return iteratorToStream(apiEndPoint(LP_DOMAINS, CSDS.class)
                    .baseURI(account)
                    .execute().body().path("baseURIs").elements())
                    .collect(Collectors
                            .toMap(e -> e.path("service").asText(), e -> e.path("baseURI").asText()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    public static <T> T apiEndPoint(final String baseUrl, final Class<T> clz) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(JacksonConverterFactory.create())
                .build().create(clz);
    }

    public static <T> T apiEndpoint(final Map<String, String> domains, final Class<T> clz) {
        return apiEndPoint(String.format("https://%s", domains.get(clz.getAnnotation(ServiceName.class).value())), clz);
    }
    public static final ObjectMapper OM = new ObjectMapper();

}
