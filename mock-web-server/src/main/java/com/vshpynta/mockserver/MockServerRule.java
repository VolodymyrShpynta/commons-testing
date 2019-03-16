package com.vshpynta.mockserver;

import lombok.SneakyThrows;
import org.json.JSONException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.*;
import static org.springframework.http.MediaType.*;
import static org.springframework.test.util.AssertionErrors.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * JUnit Rule to configure mock mockServer.
 */
public class MockServerRule implements TestRule {

    private final Supplier<RestTemplate> supplier;

    private MockRestServiceServer mockServer;

    private MockServerCallParser parser = new MockServerCallParser();

    private Map<String, MockServerCall> parsedCalls = new HashMap<>();
    private Map<String, Object> parameters = new HashMap<>();

    public MockServerRule(Supplier<RestTemplate> supplier) {
        this.supplier = supplier;
    }

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
        mockServer = MockRestServiceServer.createServer(supplier.get());
    }

    private void verifyAndReset() {
        if (mockServer != null) {
            mockServer.verify();
            mockServer.reset();
        }
    }

    @SneakyThrows
    private void mockRequest(String file) {
        mockServer
                .expect(requestMatcher(file))
                .andRespond(responseCreator(file));
    }

    private RequestMatcher requestMatcher(String file) {
        return request -> {
            MockServerCall expectedCall = getMockServerCall(file);
            URI expectedUri = expectedCall.getUri();
            assertEquals("Unexpected request host", expectedUri.getHost(), request.getURI().getHost());
            assertEquals("Unexpected request path", expectedUri.getPath(), request.getURI().getPath());

            if (!parseQuery(request.getURI().getQuery()).entrySet()
                    .containsAll(parseQuery(expectedUri.getQuery()).entrySet())) {
                throw new AssertionError(
                        format("Unexpected request query parameters expected:<%s> but was:<%s>", expectedUri, request.getURI()));
            }

            method(expectedCall.getRequestMethod()).match(request);
            HttpHeaders expectedHeaders = expectedCall.getRequestHeaders();
            for (String headerName : expectedHeaders.keySet()) {
                assertEquals("Unexpected request header", expectedHeaders.get(headerName), request.getHeaders().get(headerName));
            }

            validateBody(request, expectedCall);
        };
    }

    private ResponseCreator responseCreator(String file) {
        return request -> {
            MockServerCall expectedCall = getMockServerCall(file);
            DefaultResponseCreator responseCreator = withStatus(expectedCall.getResponseStatus())
                    .headers(expectedCall.getResponseHeaders());
            if (expectedCall.getResponseBody() != null) {
                responseCreator.body(expectedCall.getResponseBody());
            }
            parsedCalls.remove(file);
            return responseCreator.createResponse(request);
        };
    }

    private void validateBody(ClientHttpRequest request, MockServerCall expectedCall) throws IOException {
        if (isBlank(expectedCall.getRequestBody())) {
            return;
        }
        MediaType contentType = expectedCall.getRequestHeaders().getContentType();
        if (APPLICATION_FORM_URLENCODED.equals(contentType)) {
            validateFormDataBody(request, expectedCall.getRequestBody());
        } else if (APPLICATION_JSON.equals(contentType) || APPLICATION_JSON_UTF8.equals(contentType)) {
            validateJsonBody(request, expectedCall.getRequestBody());
        } else {
            MockRestRequestMatchers.content().string(expectedCall.getRequestBody()).match(request);
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null) {
            return emptyMap();
        }
        return stream(query.split("&")).collect(toMap(s -> substringBefore(s, "="), s -> substringAfter(s, "=")));
    }

    private void validateJsonBody(ClientHttpRequest request, String expectedContent) {
        if (isBlank(expectedContent)) {
            return;
        }
        MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
        try {
            JSONAssert.assertEquals(expectedContent, mockRequest.getBodyAsString(), JSONCompareMode.LENIENT);
        } catch (AssertionError | JSONException e) {
            throw new AssertionError(
                    format("Unexpected request body expected:<%s> but was:<%s>", expectedContent, mockRequest.getBodyAsString()), e);
        }
    }

    private void validateFormDataBody(ClientHttpRequest request, String expectedContent) {
        if (isBlank(expectedContent)) {
            return;
        }
        MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
        if (!asList(mockRequest.getBodyAsString().split("&"))
                .containsAll(asList(expectedContent.replaceAll("\\r|\\n", "").split("&")))) {
            throw new AssertionError(
                    format("Unexpected request body expected:<%s> but was:<%s>", expectedContent, mockRequest.getBodyAsString()));
        }
    }
}
