package com.vshpynta.mockserver;

import lombok.experimental.UtilityClass;

import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

@UtilityClass
public class UriParser {

    public static Map<String, String> parseUriQuery(String query) {
        if (query == null) {
            return emptyMap();
        }
        return stream(query.split("&")).collect(toMap(s -> substringBefore(s, "="), s -> substringAfter(s, "=")));
    }
}
