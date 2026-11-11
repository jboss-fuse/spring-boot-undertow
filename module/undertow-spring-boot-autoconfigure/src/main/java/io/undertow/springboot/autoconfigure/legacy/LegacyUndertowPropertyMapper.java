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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * {@link EnvironmentPostProcessor} that maps deprecated {@code server.undertow.*} and
 * {@code management.server.undertow.*} property keys onto the new canonical
 * {@code undertow.server.*} and {@code undertow.management.*} prefixes.
 *
 * <p>The remapped properties are added as low-priority property sources so that
 * explicitly set new-prefix keys always take precedence. Mapping results are stored on
 * the {@link ConfigurableEnvironment} via a dedicated {@link PropertySource} so that
 * {@link LegacyUndertowPropertyReport} can retrieve them per-context.
 *
 * <p>This class exists only for backward compatibility and is scheduled for removal in
 * the next feature release (phase 2 of
 * <a href="https://github.com/undertow-io/spring-boot-undertow/issues/3">#3</a>).
 */
public class LegacyUndertowPropertyMapper implements EnvironmentPostProcessor {

    static final String REMAPPED_SOURCE_NAME = "legacyUndertowProperties";

    static final String REMAPPED_ENV_SOURCE_NAME = "legacyUndertowEnvProperties";

    static final String REPORT_SOURCE_NAME = "legacyUndertowReport";

    private static final Set<String> EXCLUDED_DOT_KEYS = Set.of("server.undertow.buffers-per-region");

    private static final PrefixMapping[] DOT_MAPPINGS = {
            new PrefixMapping("server.undertow.", "undertow.server."),
            new PrefixMapping("management.server.undertow.", "undertow.management.")
    };

    private static final PrefixMapping[] ENV_MAPPINGS = {
            new PrefixMapping("SERVER_UNDERTOW_", "UNDERTOW_SERVER_"),
            new PrefixMapping("MANAGEMENT_SERVER_UNDERTOW_", "UNDERTOW_MANAGEMENT_")
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> remapped = new LinkedHashMap<>();
        Map<String, Object> remappedEnv = new LinkedHashMap<>();
        List<MappedKey> mappedKeys = new ArrayList<>();
        List<ShadowedKey> shadowedKeys = new ArrayList<>();

        MutablePropertySources propertySources = environment.getPropertySources();
        for (PropertySource<?> source : propertySources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            boolean isEnvSource = source instanceof SystemEnvironmentPropertySource;
            PrefixMapping[] mappings = isEnvSource ? ENV_MAPPINGS : DOT_MAPPINGS;
            Map<String, Object> targetMap = isEnvSource ? remappedEnv : remapped;

            for (String name : enumerable.getPropertyNames()) {
                for (PrefixMapping mapping : mappings) {
                    if (!name.startsWith(mapping.oldPrefix())) {
                        continue;
                    }
                    if (!isEnvSource && EXCLUDED_DOT_KEYS.contains(name)) {
                        break;
                    }
                    String newKey = mapping.newPrefix() + name.substring(mapping.oldPrefix().length());
                    if (environment.containsProperty(newKey)) {
                        shadowedKeys.add(new ShadowedKey(name, newKey));
                    }
                    else {
                        targetMap.putIfAbsent(newKey, enumerable.getProperty(name));
                        mappedKeys.add(new MappedKey(name, newKey));
                    }
                    break;
                }
            }
        }

        if (!remapped.isEmpty()) {
            propertySources.addLast(new MapPropertySource(REMAPPED_SOURCE_NAME, remapped));
        }
        if (!remappedEnv.isEmpty()) {
            propertySources.addLast(
                    new SystemEnvironmentPropertySource(REMAPPED_ENV_SOURCE_NAME, remappedEnv));
        }

        propertySources.addLast(new ReportPropertySource(REPORT_SOURCE_NAME, mappedKeys, shadowedKeys));
    }

    private record PrefixMapping(String oldPrefix, String newPrefix) {
    }

    record MappedKey(String oldKey, String newKey) {
    }

    record ShadowedKey(String oldKey, String newKey) {
    }

    /**
     * {@link PropertySource} used only as a data carrier for the mapping report.
     * It never resolves actual configuration properties.
     */
    static class ReportPropertySource extends PropertySource<Object> {

        private final List<MappedKey> mappedKeys;

        private final List<ShadowedKey> shadowedKeys;

        ReportPropertySource(String name, List<MappedKey> mapped, List<ShadowedKey> shadowed) {
            super(name);
            this.mappedKeys = List.copyOf(mapped);
            this.shadowedKeys = List.copyOf(shadowed);
        }

        @Override
        public Object getProperty(String name) {
            return null;
        }

        List<MappedKey> getMappedKeys() {
            return this.mappedKeys;
        }

        List<ShadowedKey> getShadowedKeys() {
            return this.shadowedKeys;
        }

    }

}
