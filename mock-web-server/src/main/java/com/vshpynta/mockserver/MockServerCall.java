package com.vshpynta.mockserver;

import lombok.Data;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Model to keep request and response data.
 */
@Data
public class MockServerCall {
    private String requestHost;
    private String requestPath;
    private HttpMethod requestMethod;
    private HttpHeaders requestHeaders = new HttpHeaders();
    private String requestBody;
    private HttpStatus responseStatus;
    private HttpHeaders responseHeaders = new HttpHeaders();
    private String responseBody;

    @SneakyThrows
    public URI getUri() {
        return new URI("https://" + getRequestHost() + getRequestPath());

    }

    public void appendRequestBody(String line) {
        if (requestBody == null) {
            requestBody = line;
        } else {
            requestBody += "\n" + line;
        }
    }

    public void appendResponseBody(String line) {
        if (responseBody == null) {
            responseBody = line;
        } else {
            responseBody += "\n" + line;
        }
    }
}
