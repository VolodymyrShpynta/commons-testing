package com.vshpynta.mockserver;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class MockServerRuleTest {

    private RestTemplate restTemplate = new RestTemplate();

    @Rule
    public MockServerRule rule = new MockServerRule(() -> restTemplate);

    @Before
    public void init() {
        restTemplate.toString();
    }

    @Test
    public void testNoMocks() {
        //should be passed without mock configuration
        assertNotNull(rule);
    }

    @Test
    @MockServerScenario("mock/servers/update-price.txt")
    public void testMockServerRule() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>("{\"price\":1111}", headers);

        ResponseEntity<String> result = restTemplate.postForEntity("http://test.com/price/update", request, String.class);
        assertThat(result.getBody()).isEqualTo("{\"oldPrice\":222,\"newPrice\":1111}");
    }

    @Test
    @MockServerScenario("mock/servers/update-price-with-param.txt")
    public void testMockServerRuleWithParameter() {
        int newPrice = 99;
        rule.setParameter("new-price-param", newPrice);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>("{\"price\":1111}", headers);

        ResponseEntity<String> result = restTemplate.postForEntity("http://test.com/price/update", request, String.class);
        assertThat(result.getBody()).isEqualTo(format("{\"oldPrice\":222,\"newPrice\":%s}", newPrice));
    }
}
