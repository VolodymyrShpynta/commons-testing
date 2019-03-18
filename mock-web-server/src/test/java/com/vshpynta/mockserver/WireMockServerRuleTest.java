package com.vshpynta.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.Response;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static com.vshpynta.mockserver.WireMockServerConfigurer.stubRequests;
import static com.vshpynta.mockserver.WireMockServerCreator.createWireMockServer;
import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class WireMockServerRuleTest {

    private static WireMockServer wireMockServer;

    private int newPrice = 99;

    @BeforeClass
    public static void initWireMockServer() {
        wireMockServer = createWireMockServer(5);
    }

    @AfterClass
    public static void shutDownWireMockServer() {
        wireMockServer.stop();
    }

    @After
    public void resetWireMockServer() {
        wireMockServer.resetAll();
    }

    @Rule
    public WireMockServerRule rule = WireMockServerRule.builder()
            .wireMockServer(wireMockServer)
            .placeholdersValues(ImmutableMap.of("new-price-param", newPrice))
            .build();

    @Test
    public void testNoMocks() {
        //should be passed without mock configuration
        assertNotNull(rule);
    }

    @Test
    @MockServerScenario("mock/servers/update-price.txt")
    public void testMockServerRule() {
        Response response = given()
                .contentType(JSON)
                .body("{\"price\":1111}")
                .when().post(format("http://localhost:%s/price/update", rule.getWireMockServer().port()));

        response.then().statusCode(SC_OK);
        String responseBody = response.getBody().asString();
        assertThat(responseBody).isEqualTo("{\n" +
                "    \"oldPrice\":222,\n" +
                "    \"newPrice\":1111\n" +
                "}");
    }

    @Test
    @MockServerScenario("mock/servers/update-price-with-param.txt")
    public void testMockServerRuleWithParameter() {
        Response response = given()
                .contentType(JSON)
                .body("{\"price\":1111}")
                .when().post(format("http://localhost:%s/price/update", rule.getWireMockServer().port()));

        response.then().statusCode(SC_OK);
        String responseBody = response.getBody().asString();
        assertThat(responseBody).isEqualTo(format("{\n" +
                "    \"oldPrice\":222,\n" +
                "    \"newPrice\":%s\n" +
                "}", newPrice));
    }

    @Test
    public void testMockServerWithParameter() {
        stubRequests(wireMockServer,
                ImmutableMap.of("new-price-param", newPrice),
                "mock/servers/update-price-with-param.txt");

        Response response = given()
                .contentType(JSON)
                .body("{\"price\":1111}")
                .when().post(format("http://localhost:%s/price/update", wireMockServer.port()));

        response.then().statusCode(SC_OK);
        String responseBody = response.getBody().asString();
        assertThat(responseBody).isEqualTo(format("{\n" +
                "    \"oldPrice\":222,\n" +
                "    \"newPrice\":%s\n" +
                "}", newPrice));
    }
}
