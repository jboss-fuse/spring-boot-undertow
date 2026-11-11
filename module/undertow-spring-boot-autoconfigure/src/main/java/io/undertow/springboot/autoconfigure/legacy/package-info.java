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

/**
 * Backward-compatibility shim that maps deprecated {@code server.undertow.*} and
 * {@code management.server.undertow.*} properties onto the new {@code undertow.server.*}
 * and {@code undertow.management.*} prefixes.
 *
 * <p>This entire package exists only for backward compatibility and is scheduled for
 * removal in the next feature release (phase 2 of
 * <a href="https://github.com/undertow-io/spring-boot-undertow/issues/3">#3</a>).
 */
package io.undertow.springboot.autoconfigure.legacy;
