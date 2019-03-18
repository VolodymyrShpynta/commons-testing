package com.vshpynta.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;

@UtilityClass
public class WireMockServerConfigurer {

    private static final String FORM_MATCHING_PARAMETER_PATTERN = ".*(?=.*%s=%s)";
    private static final String FORM_MATCHING_END_PATTERN = ".*";

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
                .forEach((headerName, headerValues) -> validateAllHeaderValues(mappingBuilder, headerName, headerValues));

        validateRequestBody(expectedCall, mappingBuilder);

        ResponseDefinitionBuilder response = aResponse();
        response.withStatus(expectedCall.getResponseStatus().value());
        expectedCall.getResponseHeaders()
                .forEach((key, value) -> response.withHeader(key, value.toArray(new String[0])));
        response.withBody(expectedCall.getResponseBody());


        mappingBuilder.willReturn(response);

        wireMockServer.stubFor(mappingBuilder);
    }

    private static void validateRequestBody(MockServerCall expectedCall, MappingBuilder mappingBuilder) {
        if (isBlank(expectedCall.getRequestBody())) {
            return;
        }

        MediaType contentType = expectedCall.getRequestHeaders().getContentType();

        if (APPLICATION_FORM_URLENCODED.equals(contentType)) {
            validateFormDataBody(expectedCall.getRequestBody(), mappingBuilder);
        } else if (APPLICATION_JSON.equals(contentType) || APPLICATION_JSON_UTF8.equals(contentType)) {
            mappingBuilder.withRequestBody(equalToJson(expectedCall.getRequestBody()));
        } else {
            mappingBuilder.withRequestBody(equalTo(expectedCall.getRequestBody()));
        }
    }

    private void validateFormDataBody(String expectedContent, MappingBuilder mappingBuilder) {
        if (isBlank(expectedContent)) {
            return;
        }
        String[] bodyParameters = expectedContent.replaceAll("\\r|\\n", EMPTY).split("&");
        StringBuilder bodyMatchingPattern = new StringBuilder();
        Arrays.stream(bodyParameters)
                .map(parameterKeyValueStr -> parameterKeyValueStr.split("="))
                .forEach(parameterKeyValue -> bodyMatchingPattern.append(format(FORM_MATCHING_PARAMETER_PATTERN, parameterKeyValue[0], parameterKeyValue[1])));
        bodyMatchingPattern.append(FORM_MATCHING_END_PATTERN);

        mappingBuilder.withRequestBody(matching(bodyMatchingPattern.toString()));
    }

    private static void validateAllHeaderValues(MappingBuilder mappingBuilder, String headerName, List<String> headerValues) {
        headerValues.forEach(headerValue -> mappingBuilder.withHeader(headerName, containing(headerValue)));
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
