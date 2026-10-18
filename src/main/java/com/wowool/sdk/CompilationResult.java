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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Result of a Wowool rule domain compilation (.wow / .woc -> .dom).
 *
 * @param success
 *            Indicates whether compilation succeeded
 * @param outputFile
 *            The generated binary .dom domain file
 * @param diagnostics
 *            Compiler warnings, syntax errors, or informational messages
 * @param rawJson
 *            Raw JSON returned by the native compiler
 */
public record CompilationResult(boolean success, Path outputFile, List<String> diagnostics, String rawJson) {
    public CompilationResult {
        diagnostics = (diagnostics == null) ? Collections.emptyList() : Collections.unmodifiableList(diagnostics);
    }
}
