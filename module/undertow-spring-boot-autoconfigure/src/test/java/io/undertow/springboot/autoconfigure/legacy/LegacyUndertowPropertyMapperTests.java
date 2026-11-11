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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import io.undertow.springboot.autoconfigure.UndertowServerProperties;
import io.undertow.springboot.autoconfigure.actuate.web.server.UndertowManagementServerProperties;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LegacyUndertowPropertyMapper}.
 */
class LegacyUndertowPropertyMapperTests {

    @Test
    void legacyServerKeysMappedToNewPrefix() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4",
                "server.undertow.threads.worker", "32");
        applyMapper(env);

        assertThat(env.getProperty("undertow.server.threads.io")).isEqualTo("4");
        assertThat(env.getProperty("undertow.server.threads.worker")).isEqualTo("32");
    }

    @Test
    void legacyManagementKeysMappedToNewPrefix() {
        MockEnvironment env = environmentWith(
                "management.server.undertow.accesslog.prefix", "mgmt_");
        applyMapper(env);

        assertThat(env.getProperty("undertow.management.accesslog.prefix")).isEqualTo("mgmt_");
    }

    @Test
    void newKeyWinsWhenBothSet() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4",
                "undertow.server.threads.io", "8");
        applyMapper(env);

        assertThat(env.getProperty("undertow.server.threads.io")).isEqualTo("8");
    }

    @Test
    void legacyKeysBindToPropertiesClass() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4",
                "server.undertow.max-http-post-size", "1MB",
                "server.undertow.accesslog.enabled", "true");
        applyMapper(env);

        UndertowServerProperties properties = new UndertowServerProperties();
        Binder binder = new Binder(ConfigurationPropertySources.get(env));
        binder.bind("undertow.server", Bindable.ofInstance(properties));

        assertThat(properties.getThreads().getIo()).isEqualTo(4);
        assertThat(properties.getMaxHttpPostSize().toBytes()).isEqualTo(1048576);
        assertThat(properties.getAccesslog().isEnabled()).isTrue();
    }

    @Test
    void legacyManagementKeysBindToPropertiesClass() {
        MockEnvironment env = environmentWith(
                "management.server.undertow.accesslog.prefix", "mgmt_");
        applyMapper(env);

        UndertowManagementServerProperties properties = new UndertowManagementServerProperties();
        Binder binder = new Binder(ConfigurationPropertySources.get(env));
        binder.bind("undertow.management", Bindable.ofInstance(properties));

        assertThat(properties.getAccesslog().getPrefix()).isEqualTo("mgmt_");
    }

    @Test
    void recordsMappedKeys() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4");
        applyMapper(env);

        assertThat(LegacyUndertowPropertyReport.getMappedKeys(env)).hasSize(1);
        assertThat(LegacyUndertowPropertyReport.getMappedKeys(env).get(0).oldKey())
                .isEqualTo("server.undertow.threads.io");
        assertThat(LegacyUndertowPropertyReport.getMappedKeys(env).get(0).newKey())
                .isEqualTo("undertow.server.threads.io");
    }

    @Test
    void recordsShadowedKeys() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4",
                "undertow.server.threads.io", "8");
        applyMapper(env);

        assertThat(LegacyUndertowPropertyReport.getShadowedKeys(env)).hasSize(1);
        assertThat(LegacyUndertowPropertyReport.getShadowedKeys(env).get(0).oldKey())
                .isEqualTo("server.undertow.threads.io");
    }

    @Test
    void noMappingWhenNoLegacyKeys() {
        MockEnvironment env = environmentWith(
                "undertow.server.threads.io", "4");
        applyMapper(env);

        assertThat(env.getPropertySources().get(LegacyUndertowPropertyMapper.REMAPPED_SOURCE_NAME)).isNull();
        assertThat(LegacyUndertowPropertyReport.getMappedKeys(env)).isEmpty();
        assertThat(LegacyUndertowPropertyReport.getShadowedKeys(env)).isEmpty();
    }

    @Test
    void highestPrecedenceSourceWins() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("cmdline",
                        Map.of("server.undertow.threads.io", "16")));
        env.setProperty("server.undertow.threads.io", "4");
        ConfigurationPropertySources.attach(env);
        applyMapper(env);

        assertThat(env.getProperty("undertow.server.threads.io")).isEqualTo("16");
    }

    @Test
    void mapKeysPreservedVerbatim() {
        MockEnvironment env = environmentWith(
                "server.undertow.options.server.ALWAYS_SET_KEEP_ALIVE", "false");
        applyMapper(env);

        assertThat(env.getProperty("undertow.server.options.server.ALWAYS_SET_KEEP_ALIVE"))
                .isEqualTo("false");
    }

    @Test
    void buffersPerRegionExcluded() {
        MockEnvironment env = environmentWith(
                "server.undertow.buffers-per-region", "16");
        applyMapper(env);

        assertThat(env.getPropertySources().get(LegacyUndertowPropertyMapper.REMAPPED_SOURCE_NAME)).isNull();
        assertThat(LegacyUndertowPropertyReport.getMappedKeys(env)).isEmpty();
    }

    @Test
    void envVarSingleWordLeafMapped() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                "env", Map.of("SERVER_UNDERTOW_THREADS_IO", "4")));
        ConfigurationPropertySources.attach(env);
        applyMapper(env);

        UndertowServerProperties props = new UndertowServerProperties();
        new Binder(ConfigurationPropertySources.get(env))
                .bind("undertow.server", Bindable.ofInstance(props));
        assertThat(props.getThreads().getIo()).isEqualTo(4);
    }

    @Test
    void envVarMultiWordLeafMapped() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                "env", Map.of("SERVER_UNDERTOW_MAX_HTTP_POST_SIZE", "7MB")));
        ConfigurationPropertySources.attach(env);
        applyMapper(env);

        UndertowServerProperties props = new UndertowServerProperties();
        new Binder(ConfigurationPropertySources.get(env))
                .bind("undertow.server", Bindable.ofInstance(props));
        assertThat(props.getMaxHttpPostSize().toBytes()).isEqualTo(7340032);
    }

    private MockEnvironment environmentWith(String... pairs) {
        MockEnvironment environment = new MockEnvironment();
        for (int i = 0; i < pairs.length; i += 2) {
            environment.setProperty(pairs[i], pairs[i + 1]);
        }
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    private void applyMapper(MockEnvironment environment) {
        new LegacyUndertowPropertyMapper().postProcessEnvironment(environment, null);
    }

}
