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

package com.nageoffer.ai.ragent.rag.core.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptTemplateLoaderTest {

    @Mock
    private ResourceLoader resourceLoader;

    private PromptTemplateLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PromptTemplateLoader(resourceLoader);
    }

    @Test
    void shouldLoadTemplateFromClasspathAndCacheIt() {
        when(resourceLoader.getResource("classpath:prompt/sample.st"))
                .thenReturn(resource("Hello {name}"));

        String first = loader.load("prompt/sample.st");
        String second = loader.load("prompt/sample.st");

        assertThat(first).isEqualTo("Hello {name}");
        assertThat(second).isEqualTo("Hello {name}");
        verify(resourceLoader, times(1)).getResource("classpath:prompt/sample.st");
    }

    @Test
    void shouldRenderSlotsAndCleanupBlankLines() {
        when(resourceLoader.getResource("classpath:prompt/render.st"))
                .thenReturn(resource("Hello {name}\n\n\nQuestion: {question}\n"));

        String rendered = loader.render(
                "prompt/render.st",
                Map.of("name", "Ragent", "question", "How to debug retrieval?"));

        assertThat(rendered).isEqualTo("Hello Ragent\n\nQuestion: How to debug retrieval?");
    }

    @Test
    void shouldRenderNamedSectionWithSlots() {
        when(resourceLoader.getResource("classpath:prompt/context.st"))
                .thenReturn(resource("""
                        --- section: question ---
                        <question>{question}</question>

                        --- section: evidence ---
                        <documents>
                        {body}
                        </documents>
                        """));

        String rendered = loader.renderSection(
                "prompt/context.st",
                "evidence",
                Map.of("body", "knowledge chunk"));

        assertThat(rendered).isEqualTo("<documents>\nknowledge chunk\n</documents>");
    }

    @Test
    void shouldThrowWhenSectionIsMissing() {
        when(resourceLoader.getResource("classpath:prompt/context.st"))
                .thenReturn(resource("""
                        --- section: existing ---
                        value
                        """));

        assertThatThrownBy(() -> loader.loadSection("prompt/context.st", "missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prompt/context.st -> missing");
    }

    private ByteArrayResource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }
}
