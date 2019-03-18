package com.vshpynta.mockserver;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

import static lombok.AccessLevel.PRIVATE;

@AllArgsConstructor(access = PRIVATE)
@Getter
public class RequestStubConfig {

    private String configFile;
    private Map<String, Object> placeholders;

    public static RequestStubConfig of(String configFile) {
        return new RequestStubConfig(configFile, ImmutableMap.of());
    }

    public static RequestStubConfig of(String configFile, Map<String, Object> placeholdersValues) {
        return new RequestStubConfig(configFile, placeholdersValues);
    }
}
