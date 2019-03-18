package com.vshpynta.mockserver;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.restassured.RestAssured.when;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@Slf4j
@UtilityClass
public class WireMockServerCreator {

    public static WireMockServer createWireMockServer(int serverStartupInitialTimeout) {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig()
                .dynamicPort()
                .notifier(new ConsoleNotifier(true)));
        wireMockServer.start();
        waitingForServerToStart(wireMockServer, serverStartupInitialTimeout);
        return wireMockServer;
    }

    private static void waitingForServerToStart(WireMockServer wireMockServer,
                                                int serviceStartupInitialTimeout) {
        await().atMost(serviceStartupInitialTimeout, SECONDS)
                .until(() -> isServiceStubAlreadyRunning(wireMockServer));
    }

    private static boolean isServiceStubAlreadyRunning(WireMockServer wireMockServer) {
        try {
            when().get(format("http://localhost:%s/__admin", wireMockServer.port()))
                    .then().statusCode(200);
            return true;
        } catch (AssertionError error) {
            log.warn("Starting wireMockServer.. Error occurred: {}", error.getMessage());
            return false;
        }
    }
}
