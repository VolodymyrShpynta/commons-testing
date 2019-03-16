package com.vshpynta.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.restassured.RestAssured.when;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.awaitility.Awaitility.await;

/**
 * JUnit Rule to configure mock mockServer.
 */
@RequiredArgsConstructor
@Slf4j
public class WiremockMockServerRule implements TestRule {

    private final int serviceStartupInitialTimeout;

    @Getter
    private WireMockServer wireMockServer;

    private MockServerCallParser parser = new MockServerCallParser();

    private Map<String, MockServerCall> parsedCalls = new HashMap<>();
    private Map<String, Object> parameters = new HashMap<>();


    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                MockServerScenario serverScenario = description.getAnnotation(MockServerScenario.class);
                if (serverScenario != null) {
                    initMockServer();
                    stream(serverScenario.value()).forEach(file -> mockRequest(file));
                }
                base.evaluate();
                verifyAndReset();
            }
        };
    }

    public void setParameter(String name, Object value) {
        parameters.put(name, value);
    }

    private MockServerCall getMockServerCall(String file) {
        return parsedCalls.computeIfAbsent(file, f -> parser.parseFile(f, parameters));
    }

    private void initMockServer() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        waitingForServiceStubStart();
    }

    private void waitingForServiceStubStart() {
        await().atMost(serviceStartupInitialTimeout, SECONDS)
                .until(this::isServiceStubAlreadyRunning);
    }

    private boolean isServiceStubAlreadyRunning() {
        try {
            when().get(format("http://localhost:%s/__admin", wireMockServer.port()))
                    .then().statusCode(200);
            return true;
        } catch (AssertionError error) {
            log.warn("Starting wireMockServer.. Error occurred: {}", error.getMessage());
            return false;
        }
    }

    private void verifyAndReset() {
        if (wireMockServer != null) {
//            wireMockServer.verify(); //TODO: add verification
//            wireMockServer.resetAll();
            wireMockServer.stop();
        }
    }

    @SneakyThrows
    private void mockRequest(String file) {
        MockServerCall expectedCall = getMockServerCall(file);

        MappingBuilder mappingBuilder = request(expectedCall.getRequestMethod().name(),
                urlPathEqualTo(expectedCall.getUri().getPath()));

        parseQuery(expectedCall.getUri().getQuery())
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

    private Map<String, String> parseQuery(String query) {
        if (query == null) {
            return emptyMap();
        }
        return stream(query.split("&")).collect(toMap(s -> substringBefore(s, "="), s -> substringAfter(s, "=")));
    }
}
