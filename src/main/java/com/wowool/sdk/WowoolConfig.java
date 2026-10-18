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

import java.nio.file.Path;

/**
 * Configuration options for initializing the {@link WowoolEngine}.
 *
 * @param licenseKey
 *            The commercial Wowool SDK key
 * @param dylibDir
 *            Directory containing the native Wowool shared libraries
 * @param wowoolRoot
 *            Directory containing the language models (lxware/wowool)
 */
public record WowoolConfig(String licenseKey, Path dylibDir, Path wowoolRoot) {
    public static Builder builder() {
        return new Builder();
    }

    public static WowoolConfig defaults() {
        return builder().build();
    }

    public static final class Builder {
        private String licenseKey;
        private Path dylibDir;
        private Path wowoolRoot;

        public Builder licenseKey(String licenseKey) {
            this.licenseKey = licenseKey;
            return this;
        }

        public Builder dylibDir(Path dylibDir) {
            this.dylibDir = dylibDir;
            return this;
        }

        public Builder wowoolRoot(Path wowoolRoot) {
            this.wowoolRoot = wowoolRoot;
            return this;
        }

        public WowoolConfig build() {
            String key = (licenseKey != null && !licenseKey.isBlank()) ? licenseKey : WowoolPaths.resolveLicenseKey();
            Path root = (wowoolRoot != null) ? wowoolRoot : WowoolPaths.resolveWowoolRoot();
            Path dylibs = (dylibDir != null) ? dylibDir : WowoolPaths.resolveDylibDir();
            return new WowoolConfig(key, dylibs, root);
        }
    }
}
