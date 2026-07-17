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

package com.nageoffer.ai.ragent.infra.http;

import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelUrlResolverTest {

    @Test
    void shouldPreferCandidateUrlWhenConfigured() {
        AIModelProperties.ProviderConfig provider = provider("https://provider.example", Map.of(
                "chat", "/v1/chat/completions"));
        AIModelProperties.ModelCandidate candidate = candidate("https://candidate.example/custom");

        String url = ModelUrlResolver.resolveUrl(provider, candidate, ModelCapability.CHAT);

        assertThat(url).isEqualTo("https://candidate.example/custom");
    }

    @Test
    void shouldJoinBaseUrlAndEndpointWithSingleSlash() {
        AIModelProperties.ProviderConfig provider = provider("https://provider.example/", Map.of(
                "embedding", "/v1/embeddings"));

        String url = ModelUrlResolver.resolveUrl(provider, null, ModelCapability.EMBEDDING);

        assertThat(url).isEqualTo("https://provider.example/v1/embeddings");
    }

    @Test
    void shouldJoinBaseUrlAndEndpointWhenBothMissSlash() {
        AIModelProperties.ProviderConfig provider = provider("https://provider.example", Map.of(
                "rerank", "v1/rerank"));

        String url = ModelUrlResolver.resolveUrl(provider, null, ModelCapability.RERANK);

        assertThat(url).isEqualTo("https://provider.example/v1/rerank");
    }

    @Test
    void shouldRejectMissingProviderBaseUrl() {
        AIModelProperties.ProviderConfig provider = provider(" ", Map.of("chat", "/v1/chat/completions"));

        assertThatThrownBy(() -> ModelUrlResolver.resolveUrl(provider, null, ModelCapability.CHAT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider baseUrl is missing");
    }

    @Test
    void shouldRejectMissingEndpointForCapability() {
        AIModelProperties.ProviderConfig provider = provider("https://provider.example", Map.of());

        assertThatThrownBy(() -> ModelUrlResolver.resolveUrl(provider, null, ModelCapability.CHAT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider endpoint is missing: chat");
    }

    private static AIModelProperties.ProviderConfig provider(String url, Map<String, String> endpoints) {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        setField(provider, "url", url);
        setField(provider, "endpoints", endpoints);
        return provider;
    }

    private static AIModelProperties.ModelCandidate candidate(String url) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        setField(candidate, "url", url);
        return candidate;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set test field: " + name, ex);
        }
    }
}
