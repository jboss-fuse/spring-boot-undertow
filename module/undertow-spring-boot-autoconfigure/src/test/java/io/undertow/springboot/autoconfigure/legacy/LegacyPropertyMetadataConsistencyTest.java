/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.springboot.autoconfigure.legacy;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.ConfigurationProperties;
import io.undertow.springboot.autoconfigure.UndertowServerProperties;
import io.undertow.springboot.autoconfigure.actuate.web.server.UndertowManagementServerProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every property defined in the {@code @ConfigurationProperties} classes
 * has a corresponding deprecated entry in
 * {@code additional-spring-configuration-metadata.json}. Fails if a property exists
 * in the class but has no deprecation entry, preventing the metadata from silently
 * drifting out of sync.
 */
class LegacyPropertyMetadataConsistencyTest {

    private static final String OLD_SERVER_PREFIX = "server.undertow";

    private static final String NEW_SERVER_PREFIX = "undertow.server";

    private static final String OLD_MANAGEMENT_PREFIX = "management.server.undertow";

    private static final String NEW_MANAGEMENT_PREFIX = "undertow.management";

    private static final String METADATA_RESOURCE = "META-INF/additional-spring-configuration-metadata.json";

    @Test
    void everyServerPropertyHasDeprecatedMetadataEntry() throws Exception {
        Map<String, String> deprecatedEntries = loadDeprecatedEntries();
        List<String> propertyKeys = discoverPropertyKeys(UndertowServerProperties.class, NEW_SERVER_PREFIX, "");
        List<String> missing = new ArrayList<>();
        for (String newKey : propertyKeys) {
            String oldKey = OLD_SERVER_PREFIX + newKey.substring(NEW_SERVER_PREFIX.length());
            if (!deprecatedEntries.containsKey(oldKey)) {
                missing.add(oldKey + " -> " + newKey);
            }
            else {
                assertThat(deprecatedEntries.get(oldKey))
                        .as("replacement for deprecated key '%s'", oldKey)
                        .isEqualTo(newKey);
            }
        }
        assertThat(missing)
                .as("Properties missing deprecated metadata entries")
                .isEmpty();
    }

    @Test
    void everyManagementPropertyHasDeprecatedMetadataEntry() throws Exception {
        Map<String, String> deprecatedEntries = loadDeprecatedEntries();
        List<String> propertyKeys = discoverPropertyKeys(
                UndertowManagementServerProperties.class, NEW_MANAGEMENT_PREFIX, "");
        List<String> missing = new ArrayList<>();
        for (String newKey : propertyKeys) {
            String oldKey = OLD_MANAGEMENT_PREFIX + newKey.substring(NEW_MANAGEMENT_PREFIX.length());
            if (!deprecatedEntries.containsKey(oldKey)) {
                missing.add(oldKey + " -> " + newKey);
            }
            else {
                assertThat(deprecatedEntries.get(oldKey))
                        .as("replacement for deprecated key '%s'", oldKey)
                        .isEqualTo(newKey);
            }
        }
        assertThat(missing)
                .as("Properties missing deprecated metadata entries")
                .isEmpty();
    }

    private Map<String, String> loadDeprecatedEntries() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (InputStream is = moduleMetadata().openStream()) {
            JsonNode root = new ObjectMapper().readTree(is);
            for (JsonNode prop : root.get("properties")) {
                JsonNode deprecation = prop.get("deprecation");
                if (deprecation != null && deprecation.has("replacement")) {
                    result.put(prop.get("name").asText(), deprecation.get("replacement").asText());
                }
            }
        }
        return result;
    }

    /**
     * Returns this module's metadata file. Spring Boot's jars contain a file with the same
     * name, and the JDK-specific surefire profiles inherited from jboss-parent put this
     * module's classes last on the test classpath, so the first match is not ours.
     */
    private URL moduleMetadata() throws Exception {
        String moduleLocation = UndertowServerProperties.class.getProtectionDomain()
                .getCodeSource().getLocation().toString();
        Enumeration<URL> candidates = getClass().getClassLoader().getResources(METADATA_RESOURCE);
        while (candidates.hasMoreElements()) {
            URL candidate = candidates.nextElement();
            if (candidate.toString().contains(moduleLocation)) {
                return candidate;
            }
        }
        throw new AssertionError(METADATA_RESOURCE + " not found in " + moduleLocation);
    }

    private List<String> discoverPropertyKeys(Class<?> propertiesClass, String prefix, String path) {
        List<String> keys = new ArrayList<>();
        for (Field field : propertiesClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String kebab = camelToKebab(field.getName());
            String fullPath = path.isEmpty() ? prefix + "." + kebab : path + "." + kebab;
            if (isNestedProperties(field)) {
                keys.addAll(discoverPropertyKeys(field.getType(), prefix, fullPath));
            }
            else {
                keys.add(fullPath);
            }
        }
        return keys;
    }

    private boolean isNestedProperties(Field field) {
        Class<?> type = field.getType();
        return type.getDeclaringClass() != null
                && (type.getDeclaringClass().isAnnotationPresent(ConfigurationProperties.class)
                        || type.getEnclosingClass() != null
                                && type.getEnclosingClass().isAnnotationPresent(ConfigurationProperties.class));
    }

    private String camelToKebab(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('-');
                }
                result.append(Character.toLowerCase(c));
            }
            else {
                result.append(c);
            }
        }
        return result.toString();
    }

}
