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

package com.wowool.sdk.internal.ffi;

import com.wowool.sdk.WowoolException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Low-level Project Panama (Foreign Function &amp; Memory API - JEP 454)
 * binding to the Wowool C++ Engine native C ABI (tir_api_*).
 */
public final class WowoolNative {

    private static volatile boolean loaded = false;
    private static MethodHandle getEngineHandle;
    private static MethodHandle getAnalyzerHandle;
    private static MethodHandle analyzeDocumentHandle;
    private static MethodHandle loadDomainHandle;
    private static MethodHandle domainProcessHandle;
    private static MethodHandle getJsonBufferHandle;
    private static MethodHandle decRefHandle;
    private static MethodHandle getLidHandle;
    private static MethodHandle lidIdentHandle;
    private static MethodHandle lidIdentSectionHandle;
    private static MethodHandle lidIdentSectionJsonHandle;
    private static MethodHandle compileDomainHandle;
    private static MethodHandle engineLanguagesHandle;
    private static MethodHandle engineInfoHandle;
    private static MethodHandle domainInfoHandle;

    private WowoolNative() {
    }

    public static synchronized void ensureLoaded(Path dylibDir) {
        if (loaded) {
            return;
        }

        try {
            Path icuData = dylibDir.resolve("libicudata.dylib");
            Path icuUc = dylibDir.resolve("libicuuc.dylib");
            Path wowoolLib = null;

            for (String candidate : WowoolPaths.expectedLibraryNames()) {
                Path p = dylibDir.resolve(candidate);
                if (Files.exists(p)) {
                    wowoolLib = p;
                    break;
                }
            }

            if (wowoolLib == null) {
                throw new WowoolException("Native library (" + String.join(" / ", WowoolPaths.expectedLibraryNames())
                        + ") not found in " + dylibDir);
            }

            if (wowoolLib.getFileName().toString().endsWith(".dll")) {
                // Load dependencies first; Windows does not search the DLL's own directory
                // for System.load'ed libraries.
                for (String dep : new String[]{"vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll",
                        "msvcp140_1.dll", "msvcp140_2.dll", "msvcp140_atomic_wait.dll", "msvcp140_codecvt_ids.dll",
                        "concrt140.dll"}) {
                    Path p = dylibDir.resolve(dep);
                    if (Files.exists(p)) {
                        System.load(p.toAbsolutePath().toString());
                    }
                }
                try (Stream<Path> dlls = Files.list(dylibDir)) {
                    List<Path> icuDlls = dlls.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("icu") && n.endsWith(".dll");
                    }).sorted((p1, p2) -> {
                        String n1 = p1.getFileName().toString();
                        String n2 = p2.getFileName().toString();
                        int r1 = n1.contains("dt") ? 0 : (n1.contains("uc") ? 1 : 2);
                        int r2 = n2.contains("dt") ? 0 : (n2.contains("uc") ? 1 : 2);
                        return Integer.compare(r1, r2);
                    }).toList();
                    for (Path p : icuDlls) {
                        System.load(p.toAbsolutePath().toString());
                    }
                }
            }

            if (Files.exists(icuData)) {
                System.load(icuData.toAbsolutePath().toString());
            }
            if (Files.exists(icuUc)) {
                System.load(icuUc.toAbsolutePath().toString());
            }
            System.load(wowoolLib.toAbsolutePath().toString());

            Linker linker = Linker.nativeLinker();

            // Ensure WOWOOL_ROOT and WOWOOL_SDK_KEY are exported into the native process
            // environment via POSIX setenv (or _putenv_s from the UCRT on Windows)
            boolean windows = WowoolPaths.isWindows();
            linker.defaultLookup().find(windows ? "_putenv_s" : "setenv").ifPresent(setenvSym -> {
                try (Arena a = Arena.ofConfined()) {
                    MethodHandle setenv = windows
                            ? linker.downcallHandle(setenvSym,
                                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                                            ValueLayout.ADDRESS))
                            : linker.downcallHandle(setenvSym, FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                    Path wowoolRoot = WowoolPaths.resolveWowoolRoot();
                    String rootStr = wowoolRoot.toString();
                    if (windows) {
                        setenv.invoke(a.allocateFrom("WOWOOL_ROOT"), a.allocateFrom(rootStr));
                    } else {
                        setenv.invoke(a.allocateFrom("WOWOOL_ROOT"), a.allocateFrom(rootStr), 1);
                    }

                    try {
                        String key = WowoolPaths.resolveLicenseKey();
                        if (key != null && !key.isBlank()) {
                            if (windows) {
                                setenv.invoke(a.allocateFrom("WOWOOL_SDK_KEY"), a.allocateFrom(key));
                            } else {
                                setenv.invoke(a.allocateFrom("WOWOOL_SDK_KEY"), a.allocateFrom(key), 1);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                } catch (Throwable ignored) {
                }
            });

            SymbolLookup lookup = SymbolLookup.loaderLookup();

            // tir_api_get_engine(char const *options, size_t options_size, tir_exception
            // *ex_info)
            getEngineHandle = linker.downcallHandle(lookup.find("tir_api_get_engine").orElseThrow(), FunctionDescriptor
                    .of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

            // tir_api_get_analyzer(tir_object *c_eng, char const *c_options, size_t
            // c_options_size, tir_exception *ex_info)
            getAnalyzerHandle = linker.downcallHandle(lookup.find("tir_api_get_analyzer").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

            // tir_api_analyze_document(tir_object *c_ana, char const *c_document, size_t
            // c_document_size, char const *c_options, size_t c_options_size, tir_exception
            // *ex_info)
            analyzeDocumentHandle = linker.downcallHandle(lookup.find("tir_api_analyze_document").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

            // tir_api_load_domain(tir_object *c_eng, const char *c_domain_name, char const
            // *c_options, size_t c_options_size, tir_exception *ex_info)
            loadDomainHandle = linker.downcallHandle(lookup.find("tir_api_load_domain").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

            // tir_api_domain_process(tir_object const *c_domain, tir_object *c_result,
            // tir_exception *ex_info)
            domainProcessHandle = linker.downcallHandle(lookup.find("tir_api_domain_process").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));

            // tir_api_get_result_json_buffer(tir_object *c_result, size_t *c_buffer_size,
            // tir_exception *ex_info)
            getJsonBufferHandle = linker.downcallHandle(lookup.find("tir_api_get_result_json_buffer").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));

            // tir_api_dec_ref(struct tir_object *object, struct tir_exception *ex_info)
            decRefHandle = linker.downcallHandle(lookup.find("tir_api_dec_ref").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            // tir_api_get_lid_engine(tir_object *c_eng, char const *options, size_t
            // options_size, tir_exception *ex_info)
            getLidHandle = linker.downcallHandle(lookup.find("tir_api_get_lid_engine").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

            // tir_api_language_identification(tir_object *c_eng, char const *c_prop, size_t
            // c_prop_sz, char *val, size_t val_sz, tir_exception *ex_info)
            lidIdentHandle = linker.downcallHandle(lookup.find("tir_api_language_identification").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

            // tir_api_language_identification_section(tir_object *c_eng, char const *doc,
            // size_t doc_sz, tir_exception *ex_info)
            lidIdentSectionHandle = linker.downcallHandle(
                    lookup.find("tir_api_language_identification_section").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

            // tir_api_language_identification_section_json(tir_object *c_lid_result,
            // tir_exception *ex_info)
            lidIdentSectionJsonHandle = linker.downcallHandle(
                    lookup.find("tir_api_language_identification_section_json").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            // tir_api_compile_domain(char const *c_options, size_t c_options_size,
            // tir_exception *ex_info)
            compileDomainHandle = linker.downcallHandle(lookup.find("tir_api_compile_domain").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS));

            // tir_api_engine_languages(tir_object *c_eng, size_t *c_buffer_size,
            // tir_exception *ex_info)
            lookup.find("tir_api_engine_languages")
                    .ifPresent(sym -> engineLanguagesHandle = linker.downcallHandle(sym, FunctionDescriptor
                            .of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)));

            // tir_api_engine_info(tir_object *c_eng, size_t *c_buffer_size, tir_exception
            // *ex_info)
            lookup.find("tir_api_engine_info")
                    .ifPresent(sym -> engineInfoHandle = linker.downcallHandle(sym, FunctionDescriptor
                            .of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)));

            // tir_api_domain_info(tir_object *c_domain, tir_exception *ex_info)
            lookup.find("tir_api_domain_info").ifPresent(sym -> domainInfoHandle = linker.downcallHandle(sym,
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)));

            loaded = true;
        } catch (Throwable t) {
            throw new WowoolException("Failed to load Wowool native C++ libraries: " + t.getMessage(), t);
        }
    }

    public static MemorySegment createEngine(String optionsJson, Arena arena, MemorySegment exInfo) {
        try {
            MemorySegment optSeg = arena.allocateFrom(optionsJson);
            long len = optionsJson.getBytes().length;
            MemorySegment ptr = (MemorySegment) getEngineHandle.invokeExact(optSeg, len, exInfo);
            checkException("tir_api_get_engine", exInfo);
            return ptr;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment createAnalyzer(MemorySegment enginePtr, String optionsJson, Arena arena,
            MemorySegment exInfo) {
        try {
            MemorySegment optSeg = arena.allocateFrom(optionsJson);
            long len = optSeg.byteSize() - 1;
            MemorySegment ptr = (MemorySegment) getAnalyzerHandle.invokeExact(enginePtr, optSeg, len, exInfo);
            checkException("tir_api_get_analyzer", exInfo);
            return ptr;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment loadDomain(MemorySegment enginePtr, String domainPath, String optionsJson, Arena arena,
            MemorySegment exInfo) {
        try {
            MemorySegment pathSeg = arena.allocateFrom(domainPath);
            MemorySegment optSeg = arena.allocateFrom(optionsJson);
            long len = optSeg.byteSize() - 1;
            MemorySegment ptr = (MemorySegment) loadDomainHandle.invokeExact(enginePtr, pathSeg, optSeg, len, exInfo);
            checkException("tir_api_load_domain", exInfo);
            return ptr;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment analyzeDocument(MemorySegment analyzerPtr, String text, String optionsJson, Arena arena,
            MemorySegment exInfo) {
        try {
            MemorySegment textSeg = arena.allocateFrom(text);
            long textLen = textSeg.byteSize() - 1;
            MemorySegment optSeg = arena.allocateFrom(optionsJson);
            long optLen = optSeg.byteSize() - 1;
            MemorySegment ptr = (MemorySegment) analyzeDocumentHandle.invokeExact(analyzerPtr, textSeg, textLen, optSeg,
                    optLen, exInfo);
            checkException("tir_api_analyze_document", exInfo);
            return ptr;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment processDomain(MemorySegment domainPtr, MemorySegment resultPtr, MemorySegment exInfo) {
        try {
            MemorySegment ptr = (MemorySegment) domainProcessHandle.invokeExact(domainPtr, resultPtr, exInfo);
            checkException("tir_api_domain_process", exInfo);
            return ptr;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static byte[] getResultJsonBytes(MemorySegment resultPtr, Arena arena, MemorySegment exInfo) {
        try {
            MemorySegment sizeSeg = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment jsonBuffer = (MemorySegment) getJsonBufferHandle.invokeExact(resultPtr, sizeSeg, exInfo);
            checkException("tir_api_get_result_json_buffer", exInfo);
            long size = sizeSeg.get(ValueLayout.JAVA_LONG, 0);
            return jsonBuffer.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment createLid(MemorySegment enginePtr, String optionsJson, Arena arena,
            MemorySegment exInfo) {
        try {
            MemorySegment optSeg = arena.allocateFrom(optionsJson);
            long len = optSeg.byteSize() - 1;
            MemorySegment ptr = (MemorySegment) getLidHandle.invokeExact(enginePtr, optSeg, len, exInfo);
            checkException("tir_api_get_lid_engine", exInfo);
            return ptr;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static String identifyLanguage(MemorySegment lidPtr, String text, Arena arena, MemorySegment exInfo) {
        try {
            MemorySegment textSeg = arena.allocateFrom(text);
            long textLen = textSeg.byteSize() - 1;
            MemorySegment buf = arena.allocate(100);
            long bytes = (long) lidIdentHandle.invokeExact(lidPtr, textSeg, textLen, buf, 100L, exInfo);
            checkException("tir_api_language_identification", exInfo);
            if (bytes <= 0) {
                return "unknown";
            }
            long safeLen = Math.min(bytes, 100L);
            return new String(buf.reinterpret(safeLen).toArray(ValueLayout.JAVA_BYTE)).trim();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static String identifyLanguageSectionJson(MemorySegment lidPtr, String text, Arena arena,
            MemorySegment exInfo) {
        try {
            MemorySegment textSeg = arena.allocateFrom(text);
            long textLen = textSeg.byteSize() - 1;
            MemorySegment secResPtr = (MemorySegment) lidIdentSectionHandle.invokeExact(lidPtr, textSeg, textLen,
                    exInfo);
            checkException("tir_api_language_identification_section", exInfo);

            MemorySegment jsonPtr = (MemorySegment) lidIdentSectionJsonHandle.invokeExact(secResPtr, exInfo);
            checkException("tir_api_language_identification_section_json", exInfo);
            String json = (jsonPtr == null || jsonPtr.address() == 0)
                    ? "{}"
                    : jsonPtr.reinterpret(Long.MAX_VALUE).getString(0);
            decRef(secResPtr, exInfo);
            return json;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static String compileDomain(String optionsJson, Arena arena, MemorySegment exInfo) {
        try {
            MemorySegment optSeg = arena.allocateFrom(optionsJson);
            long len = optSeg.byteSize() - 1;
            MemorySegment resPtr = (MemorySegment) compileDomainHandle.invokeExact(optSeg, len, exInfo);
            checkException("tir_api_compile_domain", exInfo);
            if (resPtr == null || resPtr.address() == 0) {
                return "{}";
            }
            return resPtr.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static String getEngineLanguages(MemorySegment enginePtr, Arena arena, MemorySegment exInfo) {
        if (engineLanguagesHandle == null) {
            return "{}";
        }
        try {
            MemorySegment sizeSeg = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment resPtr = (MemorySegment) engineLanguagesHandle.invokeExact(enginePtr, sizeSeg, exInfo);
            checkException("tir_api_engine_languages", exInfo);
            if (resPtr == null || resPtr.address() == 0) {
                return "{}";
            }
            return resPtr.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static String getEngineInfo(MemorySegment enginePtr, Arena arena, MemorySegment exInfo) {
        if (engineInfoHandle == null) {
            return "{}";
        }
        try {
            MemorySegment sizeSeg = arena.allocate(ValueLayout.JAVA_LONG);
            MemorySegment resPtr = (MemorySegment) engineInfoHandle.invokeExact(enginePtr, sizeSeg, exInfo);
            checkException("tir_api_engine_info", exInfo);
            if (resPtr == null || resPtr.address() == 0) {
                return "{}";
            }
            return resPtr.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static String getDomainInfo(MemorySegment domainPtr, MemorySegment exInfo) {
        if (domainInfoHandle == null) {
            return "{}";
        }
        try {
            MemorySegment resPtr = (MemorySegment) domainInfoHandle.invokeExact(domainPtr, exInfo);
            checkException("tir_api_domain_info", exInfo);
            if (resPtr == null || resPtr.address() == 0) {
                return "{}";
            }
            return resPtr.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void decRef(MemorySegment objPtr, MemorySegment exInfo) {
        if (objPtr == null || objPtr.address() == 0) {
            return;
        }
        try {
            decRefHandle.invokeExact(objPtr, exInfo);
        } catch (Throwable t) {
            // suppress or log
        }
    }

    public static MemorySegment allocateExceptionInfo(Arena arena) {
        MemorySegment seg = arena.allocate(1032);
        seg.fill((byte) 0);
        return seg;
    }

    public static void checkException(String op, MemorySegment exInfo) {
        int exType = exInfo.get(ValueLayout.JAVA_INT, 0);
        if (exType != 0) {
            String errorMsg = exInfo.getString(4);
            throw new WowoolException("Native Wowool error during '" + op + "': " + errorMsg, exType);
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof WowoolException we) {
            return we;
        }
        if (t instanceof RuntimeException re) {
            return re;
        }
        return new WowoolException("Unexpected native exception: " + t.getMessage(), t);
    }
}
