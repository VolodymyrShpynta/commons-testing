package com.vshpynta.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
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

import static com.vshpynta.mockserver.WireMockServerConfigurer.stubRequests;
import static java.util.Arrays.stream;

/**
 * JUnit Rule to configure mock mockServer.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class WireMockServerRule implements TestRule {

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
            stream(serverScenario.value())
                    .map(configFile -> RequestStubConfig.of(configFile, placeholdersValues))
                    .forEach(requestStubConfig -> stubRequests(wireMockServer, requestStubConfig));
        }
    }

    private void verifyAndResetMockServer() {
        if (wireMockServer != null) {
            wireMockServer.resetAll();
        }
    }
}
