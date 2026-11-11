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

import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * {@link ApplicationListener} that logs a consolidated WARN when deprecated Undertow
 * property prefixes were used. The report is deferred until
 * {@link ApplicationReadyEvent} so the application starts up cleanly before printing.
 *
 * <p>Report data is read from the {@link ConfigurableEnvironment} where it was stored
 * by {@link LegacyUndertowPropertyMapper}, ensuring correct behavior with multiple
 * application contexts, DevTools restarts, and {@code @SpringBootTest}.
 *
 * <p>This class exists only for backward compatibility and is scheduled for removal in
 * the next feature release (phase 2 of
 * <a href="https://github.com/undertow-io/spring-boot-undertow/issues/3">#3</a>).
 */
public class LegacyUndertowPropertyReport implements ApplicationListener<ApplicationReadyEvent> {

    private static final Log logger = LogFactory.getLog(LegacyUndertowPropertyReport.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
        LegacyUndertowPropertyMapper.ReportPropertySource report = getReportSource(env);
        if (report == null) {
            return;
        }

        List<LegacyUndertowPropertyMapper.MappedKey> mapped = report.getMappedKeys();
        List<LegacyUndertowPropertyMapper.ShadowedKey> shadowed = report.getShadowedKeys();

        if (mapped.isEmpty() && shadowed.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder();
        if (!mapped.isEmpty()) {
            message.append("The following Undertow properties use deprecated prefixes ")
                    .append("and will be removed in a future release. Please migrate:\n");
            for (LegacyUndertowPropertyMapper.MappedKey key : mapped) {
                message.append("  ").append(key.oldKey()).append(" -> ").append(key.newKey()).append('\n');
            }
        }
        if (!shadowed.isEmpty()) {
            message.append("The following deprecated Undertow properties were ignored ")
                    .append("because the new key is also set:\n");
            for (LegacyUndertowPropertyMapper.ShadowedKey key : shadowed) {
                message.append("  ").append(key.oldKey())
                        .append(" (ignored, using ").append(key.newKey()).append(")\n");
            }
        }

        logger.warn(message.toString().stripTrailing());
    }

    static LegacyUndertowPropertyMapper.ReportPropertySource getReportSource(ConfigurableEnvironment env) {
        PropertySource<?> source = env.getPropertySources()
                .get(LegacyUndertowPropertyMapper.REPORT_SOURCE_NAME);
        if (source instanceof LegacyUndertowPropertyMapper.ReportPropertySource reportSource) {
            return reportSource;
        }
        return null;
    }

    static List<LegacyUndertowPropertyMapper.MappedKey> getMappedKeys(ConfigurableEnvironment env) {
        LegacyUndertowPropertyMapper.ReportPropertySource report = getReportSource(env);
        return (report != null) ? report.getMappedKeys() : Collections.emptyList();
    }

    static List<LegacyUndertowPropertyMapper.ShadowedKey> getShadowedKeys(ConfigurableEnvironment env) {
        LegacyUndertowPropertyMapper.ReportPropertySource report = getReportSource(env);
        return (report != null) ? report.getShadowedKeys() : Collections.emptyList();
    }

}
