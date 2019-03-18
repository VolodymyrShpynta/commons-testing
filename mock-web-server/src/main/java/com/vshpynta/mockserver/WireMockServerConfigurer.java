package com.vshpynta.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

@UtilityClass
public class WireMockServerConfigurer {

    public static void stubRequests(WireMockServer wireMockServer,
                                   RequestStubConfig... requestStubConfigs) {
        Arrays.stream(requestStubConfigs)
                .forEach(requestStubConfig -> stubRequest(wireMockServer, requestStubConfig));

    }

    public static void stubRequests(WireMockServer wireMockServer,
                                    Map<String, Object> placeholders,
                                    String... configFiles) {
        Arrays.stream(configFiles)
                .map(configFile -> RequestStubConfig.of(configFile, placeholders))
                .forEach(requestStubConfig -> stubRequest(wireMockServer, requestStubConfig));

    }

    public static void stubRequests(WireMockServer wireMockServer,
                                    String... configFiles) {
        Arrays.stream(configFiles)
                .map(configFile -> RequestStubConfig.of(configFile, ImmutableMap.of()))
                .forEach(requestStubConfig -> stubRequest(wireMockServer, requestStubConfig));

    }

    public static void stubRequests(WireMockServer wireMockServer,
                                    List<RequestStubConfig> requestStubConfigs) {
        requestStubConfigs
                .forEach(requestStubConfig -> stubRequest(wireMockServer, requestStubConfig));

    }

    @SneakyThrows
    private static void stubRequest(WireMockServer wireMockServer,
                                   RequestStubConfig requestStubConfig) {
        MockServerCall expectedCall = getMockServerCall(requestStubConfig.getConfigFile(),
                requestStubConfig.getPlaceholders());

        MappingBuilder mappingBuilder = request(expectedCall.getRequestMethod().name(),
                urlPathEqualTo(expectedCall.getUri().getPath()));

        parseUriQuery(expectedCall.getUri().getQuery())
                .forEach((key, value) -> mappingBuilder.withQueryParam(key, equalTo(value)));

        expectedCall.getRequestHeaders()
                .forEach((key, value) -> mappingBuilder.withHeader(key, containing(value.get(0)))); //TODO: verify all

        if (isNotBlank(expectedCall.getRequestBody())) {
            mappingBuilder.withRequestBody(equalToJson(expectedCall.getRequestBody())); //TODO: process different body types
        }

        ResponseDefinitionBuilder response = aResponse();
        response.withStatus(expectedCall.getResponseStatus().value());
        expectedCall.getResponseHeaders()
                .forEach((key, value) -> response.withHeader(key, value.toArray(new String[0])));
        response.withBody(expectedCall.getResponseBody());


        mappingBuilder.willReturn(response);

        wireMockServer.stubFor(mappingBuilder);
    }

    private static MockServerCall getMockServerCall(String requestToResponseMappingFile,
                                                    Map<String, Object> placeholdersValues) {
        return MockServerCallParser.parseFile(requestToResponseMappingFile, placeholdersValues);
    }

    public static Map<String, String> parseUriQuery(String query) {
        if (query == null) {
            return emptyMap();
        }
        return stream(query.split("&")).collect(toMap(s -> substringBefore(s, "="), s -> substringAfter(s, "=")));
    }
}
