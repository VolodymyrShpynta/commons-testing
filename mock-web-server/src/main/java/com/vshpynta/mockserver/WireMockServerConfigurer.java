package com.vshpynta.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.vshpynta.mockserver.UriParser.parseUriQuery;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@UtilityClass
public class WireMockServerConfigurer {

    @SneakyThrows
    public static void mockRequest(WireMockServer wireMockServer,
                                   String requestToResponseMappingFile,
                                   Map<String, Object> placeholdersValues) {
        MockServerCall expectedCall = getMockServerCall(requestToResponseMappingFile, placeholdersValues);

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
}
