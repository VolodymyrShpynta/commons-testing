package com.vshpynta.mockserver;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.function.BiFunction;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Reads data from text file into Mock Server model.
 */
public class MockServerCallParser {

    /**
     * Line parser result.
     */
    enum Result {
        NEXT_LINE, NEXT_LINE_NEXT_PARSER, NEXT_PARSER
    }

    private static final List<BiFunction<String, MockServerCall, Result>> lineParsers = asList(
            //request path and method
            (line, call) -> {
                String[] parts = line.split(" ");
                call.setRequestMethod(HttpMethod.resolve(parts[0]));
                call.setRequestPath(parts[1]);
                return Result.NEXT_LINE_NEXT_PARSER;
            },
            //request host
            (line, call) -> {
                call.setRequestHost(line.split(": ")[1].trim());
                return Result.NEXT_LINE_NEXT_PARSER;
            },
            //request headers
            (line, call) -> {
                if (isBlank(line)) {
                    return Result.NEXT_LINE_NEXT_PARSER;
                }
                String[] parts = line.split(": ");
                call.getRequestHeaders().add(parts[0].trim(), parts[1].trim());
                return Result.NEXT_LINE;
            },
            //request body
            (line, call) -> {
                if (isBlank(line)) {
                    return Result.NEXT_LINE_NEXT_PARSER;
                }
                if (line.startsWith("HTTP")) {
                    return Result.NEXT_PARSER;
                }
                call.appendRequestBody(line);
                return Result.NEXT_LINE;
            },
            //response status
            (line, call) -> {
                String[] parts = line.split(" ");
                call.setResponseStatus(HttpStatus.valueOf(Integer.parseInt(parts[1])));
                return Result.NEXT_LINE_NEXT_PARSER;
            },
            //response headers
            (line, call) -> {
                if (isBlank(line)) {
                    return Result.NEXT_LINE_NEXT_PARSER;
                }
                String[] parts = line.split(": ");
                call.getResponseHeaders().add(parts[0].trim(), parts[1].trim());
                return Result.NEXT_LINE;
            },
            //response body
            (line, call) -> {
                if (isBlank(line)) {
                    return Result.NEXT_LINE_NEXT_PARSER;
                }
                call.appendResponseBody(line);
                return Result.NEXT_LINE;
            }
    );

    @SneakyThrows
    public MockServerCall parseFile(String scenarioFileName, Map<String, Object> context) {
        return Optional.ofNullable(this.getClass().getClassLoader().getResourceAsStream(scenarioFileName))
                .map(is -> parseFile(is, context))
                .orElseThrow(() -> new IllegalArgumentException("Cannot parse mock server call file: " + scenarioFileName));
    }

    @SneakyThrows
    private MockServerCall parseFile(InputStream scenarioFile, Map<String, Object> context) {
        try (Reader reader = new InputStreamReader(scenarioFile)) {
            final Iterator<BiFunction<String, MockServerCall, Result>> parsers = lineParsers.iterator();
            final Iterator<String> lines = Optional.of(reader)
                    .map(this::readLines)
                    .map(list -> prepareLines(list, context))
                    .map(List::iterator)
                    .orElseThrow(() -> new IllegalArgumentException("Cannot parse mock server call file"));
            final MockServerCall call = new MockServerCall();
            BiFunction<String, MockServerCall, Result> parser = parsers.next();
            String line = nextLine(lines);
            while (line != null) {
                Result result = parser.apply(line, call);
                if (!Result.NEXT_LINE.equals(result)) {
                    parser = parsers.next();
                }
                if (!Result.NEXT_PARSER.equals(result)) {
                    line = nextLine(lines);
                }
            }
            return call;
        }
    }

    private List<String> prepareLines(List<String> lines, Map<String, Object> context) {
        List<String> preparedLines = new ArrayList<>();
        boolean lastEmpty = true;
        for (String line : lines) {
            if (line.startsWith("#")) {
                continue;
            }
            boolean emptyLine = isBlank(line);
            if (!emptyLine || !lastEmpty) {
                preparedLines.add(applyContextParameters(line, context));
            }
            lastEmpty = emptyLine;
        }
        return preparedLines;
    }

    private String applyContextParameters(String line, Map<String, Object> context) {
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            line = line.replace("$" + entry.getKey(), Objects.toString(entry.getValue(), ""));
        }
        return line;
    }

    private String nextLine(Iterator<String> lines) {
        return lines.hasNext() ? lines.next() : null;
    }

    @SneakyThrows
    private List<String> readLines(Reader reader) {
        return IOUtils.readLines(reader);
    }

}
