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

import java.io.File;
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
 * Backward-compatibility tests verifying that legacy property prefixes still bind
 * correctly through the {@link LegacyUndertowPropertyMapper} shim.
 */
class LegacyPropertyBackwardCompatibilityTests {

    @Test
    void oldKeysAloneStillBind() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4",
                "server.undertow.threads.worker", "32",
                "server.undertow.max-http-post-size", "4MB",
                "server.undertow.buffer-size", "16KB",
                "server.undertow.direct-buffers", "true",
                "server.undertow.eager-filter-init", "false",
                "server.undertow.max-parameters", "100",
                "server.undertow.max-headers", "50",
                "server.undertow.max-cookies", "25",
                "server.undertow.decode-url", "false",
                "server.undertow.always-set-keep-alive", "false",
                "server.undertow.no-request-timeout", "30s",
                "server.undertow.preserve-path-on-forward", "true",
                "server.undertow.accesslog.enabled", "true",
                "server.undertow.accesslog.pattern", "combined",
                "server.undertow.accesslog.prefix", "app_log.",
                "server.undertow.accesslog.suffix", "txt",
                "server.undertow.accesslog.dir", "/var/log",
                "server.undertow.accesslog.rotate", "false");
        applyMapper(env);
        UndertowServerProperties props = bindServer(env);

        assertThat(props.getThreads().getIo()).isEqualTo(4);
        assertThat(props.getThreads().getWorker()).isEqualTo(32);
        assertThat(props.getMaxHttpPostSize().toBytes()).isEqualTo(4194304);
        assertThat(props.getBufferSize().toBytes()).isEqualTo(16384);
        assertThat(props.getDirectBuffers()).isTrue();
        assertThat(props.isEagerFilterInit()).isFalse();
        assertThat(props.getMaxParameters()).isEqualTo(100);
        assertThat(props.getMaxHeaders()).isEqualTo(50);
        assertThat(props.getMaxCookies()).isEqualTo(25);
        assertThat(props.isDecodeUrl()).isFalse();
        assertThat(props.isAlwaysSetKeepAlive()).isFalse();
        assertThat(props.getNoRequestTimeout().getSeconds()).isEqualTo(30);
        assertThat(props.isPreservePathOnForward()).isTrue();
        assertThat(props.getAccesslog().isEnabled()).isTrue();
        assertThat(props.getAccesslog().getPattern()).isEqualTo("combined");
        assertThat(props.getAccesslog().getPrefix()).isEqualTo("app_log.");
        assertThat(props.getAccesslog().getSuffix()).isEqualTo("txt");
        assertThat(props.getAccesslog().getDir()).isEqualTo(new File("/var/log"));
        assertThat(props.getAccesslog().isRotate()).isFalse();
    }

    @Test
    void newKeysAloneBind() {
        MockEnvironment env = environmentWith(
                "undertow.server.threads.io", "8",
                "undertow.server.threads.worker", "64",
                "undertow.server.accesslog.enabled", "true");
        applyMapper(env);
        UndertowServerProperties props = bindServer(env);

        assertThat(props.getThreads().getIo()).isEqualTo(8);
        assertThat(props.getThreads().getWorker()).isEqualTo(64);
        assertThat(props.getAccesslog().isEnabled()).isTrue();
    }

    @Test
    void newKeyWinsWhenBothAreSet() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4",
                "undertow.server.threads.io", "16",
                "server.undertow.accesslog.enabled", "false",
                "undertow.server.accesslog.enabled", "true");
        applyMapper(env);
        UndertowServerProperties props = bindServer(env);

        assertThat(props.getThreads().getIo()).isEqualTo(16);
        assertThat(props.getAccesslog().isEnabled()).isTrue();
    }

    @Test
    void warnReportListsExactDeprecatedKeys() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4",
                "server.undertow.accesslog.enabled", "true");
        applyMapper(env);

        assertThat(LegacyUndertowPropertyReport.getMappedKeys(env))
                .extracting(LegacyUndertowPropertyMapper.MappedKey::oldKey)
                .containsExactlyInAnyOrder(
                        "server.undertow.threads.io",
                        "server.undertow.accesslog.enabled");
        assertThat(LegacyUndertowPropertyReport.getMappedKeys(env))
                .extracting(LegacyUndertowPropertyMapper.MappedKey::newKey)
                .containsExactlyInAnyOrder(
                        "undertow.server.threads.io",
                        "undertow.server.accesslog.enabled");
    }

    @Test
    void warnReportListsShadowedKeys() {
        MockEnvironment env = environmentWith(
                "server.undertow.threads.io", "4",
                "undertow.server.threads.io", "16");
        applyMapper(env);

        assertThat(LegacyUndertowPropertyReport.getShadowedKeys(env))
                .extracting(LegacyUndertowPropertyMapper.ShadowedKey::oldKey)
                .containsExactly("server.undertow.threads.io");
    }

    @Test
    void envVarStyleKeysAreMapped() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                "env", Map.of(
                        "SERVER_UNDERTOW_THREADS_IO", "4",
                        "SERVER_UNDERTOW_MAX_HTTP_POST_SIZE", "7MB")));
        ConfigurationPropertySources.attach(env);
        applyMapper(env);

        UndertowServerProperties props = bindServer(env);
        assertThat(props.getThreads().getIo()).isEqualTo(4);
        assertThat(props.getMaxHttpPostSize().toBytes()).isEqualTo(7340032);
    }

    @Test
    void managementServerLegacyKeysAreMapped() {
        MockEnvironment env = environmentWith(
                "management.server.undertow.accesslog.prefix", "mgmt_access_");
        applyMapper(env);
        UndertowManagementServerProperties props = bindManagement(env);

        assertThat(props.getAccesslog().getPrefix()).isEqualTo("mgmt_access_");
    }

    @Test
    void managementServerNewKeyWins() {
        MockEnvironment env = environmentWith(
                "management.server.undertow.accesslog.prefix", "old_",
                "undertow.management.accesslog.prefix", "new_");
        applyMapper(env);
        UndertowManagementServerProperties props = bindManagement(env);

        assertThat(props.getAccesslog().getPrefix()).isEqualTo("new_");
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

    private UndertowServerProperties bindServer(MockEnvironment env) {
        UndertowServerProperties props = new UndertowServerProperties();
        new Binder(ConfigurationPropertySources.get(env))
                .bind("undertow.server", Bindable.ofInstance(props));
        return props;
    }

    private UndertowManagementServerProperties bindManagement(MockEnvironment env) {
        UndertowManagementServerProperties props = new UndertowManagementServerProperties();
        new Binder(ConfigurationPropertySources.get(env))
                .bind("undertow.management", Bindable.ofInstance(props));
        return props;
    }

}
