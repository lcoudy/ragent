/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.infra.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LLMResponseCleanerTest {

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(LLMResponseCleaner.stripMarkdownCodeFence(null)).isNull();
    }

    @Test
    void shouldStripLanguageMarkedCodeFence() {
        String raw = """
                ```json
                {"ok": true}
                ```
                """;

        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);

        assertThat(cleaned).isEqualTo("{\"ok\": true}");
    }

    @Test
    void shouldStripPlainCodeFence() {
        String raw = """
                ```
                answer text
                ```
                """;

        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);

        assertThat(cleaned).isEqualTo("answer text");
    }

    @Test
    void shouldStripHyphenatedLanguageMarker() {
        String raw = """
                ```json-lines
                {"event":"message"}
                ```
                """;

        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);

        assertThat(cleaned).isEqualTo("{\"event\":\"message\"}");
    }

    @Test
    void shouldTrimPlainTextWithoutChangingContent() {
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence("  plain answer  ");

        assertThat(cleaned).isEqualTo("plain answer");
    }
}
