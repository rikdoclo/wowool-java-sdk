/*
 * Copyright (c) 2026 Rik Doclo. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wowool.sdk;

import com.wowool.sdk.internal.ffi.WowoolPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformDiagnosticsTest {

    @Test
    @DisplayName("Verify platform architecture detection and library naming conventions")
    void testPlatformArchitectureDetection() {
        String arch = WowoolPaths.normalizedArch();
        assertNotNull(arch);
        assertFalse(arch.isBlank());

        List<String> names = WowoolPaths.expectedLibraryNames();
        assertNotNull(names);
        assertFalse(names.isEmpty());

        if (WowoolPaths.isMac()) {
            assertTrue(names.contains("libwowool.3.3.2.dylib") || names.contains("libwowool.dylib"));
        } else if (WowoolPaths.isLinux()) {
            assertTrue(names.contains("libwowool.so.3.3.2") || names.contains("libwowool.so"));
        }
    }

    @Test
    @DisplayName("Verify resolution of active environment or fallback diagnostics")
    void testActiveEnvironmentResolution() {
        Path dylibDir = WowoolPaths.resolveDylibDir();
        assertNotNull(dylibDir);
        assertTrue(dylibDir.toFile().exists());

        Path rootDir = WowoolPaths.resolveWowoolRoot();
        assertNotNull(rootDir);
        assertTrue(rootDir.toFile().exists());

        Optional<Path> lang = WowoolPaths.resolveLanguageFile(rootDir, "dutch");
        assertTrue(lang.isPresent());
        assertTrue(lang.get().toFile().exists());

        Optional<Path> dom = WowoolPaths.resolveDomainFile(rootDir, "dutch", "entity");
        assertTrue(dom.isPresent());
        assertTrue(dom.get().toFile().exists());
    }
}
