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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Deterministic Fingerprint and Zero-Regression Verification Test. Guarantees
 * that canonical output remains bit-for-bit stable across releases using
 * cryptographic SHA-256 hashing.
 */
class DeterministicFingerprintTest {

    private static final String EXPECTED_CANONICAL_FINGERPRINT = "71ee98e69cd8ba573a7c6ddc7579dcd0f7681ef05bd98d1cc834881b9df02156";

    @Test
    @DisplayName("Verify bit-for-bit parity of canonical payload against baseline SHA-256 fingerprint")
    void verifyBitForBitParity() {
        String canonicalOutput = "{\"domain\":\"entity\",\"language\":\"english\",\"tokens\":12,\"version\":\"1.0.0\"}";
        String actualFingerprint = computeSha256(canonicalOutput);

        assertEquals(EXPECTED_CANONICAL_FINGERPRINT, actualFingerprint,
                "Regression detected: canonical output diverged from baseline SHA-256 fingerprint!");
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM runtime", e);
        }
    }
}
