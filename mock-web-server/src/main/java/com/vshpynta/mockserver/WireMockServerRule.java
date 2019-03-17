package com.vshpynta.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.restassured.RestAssured.when;
import static com.vshpynta.mockserver.WireMockServerConfigurer.mockRequest;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

/**
 * JUnit Rule to configure mock mockServer.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class WireMockServerRule implements TestRule {

    @Builder.Default
    private int serviceStartupInitialTimeout = 5;
    @Builder.Default
    private Map<String, Object> placeholdersValues = ImmutableMap.of();

    @Getter
    private WireMockServer wireMockServer;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                configureMockServer(description.getAnnotation(MockServerScenario.class));
                base.evaluate();
                verifyAndResetMockServer();
            }
        };
    }

    private void configureMockServer(MockServerScenario serverScenario) {
        if (serverScenario != null) {
            initMockServer();
            stream(serverScenario.value())
                    .forEach(requestToResponseMappingFile -> mockRequest(wireMockServer,
                            requestToResponseMappingFile,
                            placeholdersValues));
        }
    }

    private void initMockServer() {
        wireMockServer = new WireMockServer(wireMockConfig()
                .dynamicPort()
                .notifier(new ConsoleNotifier(true)));
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

    private void verifyAndResetMockServer() {
        if (wireMockServer != null) {
//            wireMockServer.resetAll();
            wireMockServer.stop();
        }
    }
}
