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

package com.nageoffer.ai.ragent.infra.model;

import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRoutingExecutorTest {

    @Test
    void executeWithFallbackShouldTryNextCandidateAfterFailure() {
        ModelHealthStore healthStore = newHealthStore();
        ModelRoutingExecutor executor = new ModelRoutingExecutor(healthStore);
        List<String> calledModels = new ArrayList<>();

        String response = executor.executeWithFallback(
                ModelCapability.CHAT,
                List.of(target("primary"), target("backup")),
                Function.identity(),
                (client, target) -> {
                    calledModels.add(target.id());
                    if ("primary".equals(target.id())) {
                        throw new IllegalStateException("primary failed");
                    }
                    return "response from " + target.id();
                });

        assertThat(response).isEqualTo("response from backup");
        assertThat(calledModels).containsExactly("primary", "backup");
        assertThat(healthStore.isUnavailable("primary")).isTrue();
        assertThat(healthStore.isUnavailable("backup")).isFalse();
    }

    @Test
    void executeWithFallbackShouldSkipUnavailableCandidate() {
        ModelHealthStore healthStore = newHealthStore();
        healthStore.markFailure("primary");
        ModelRoutingExecutor executor = new ModelRoutingExecutor(healthStore);
        List<String> calledModels = new ArrayList<>();

        String response = executor.executeWithFallback(
                ModelCapability.CHAT,
                List.of(target("primary"), target("backup")),
                Function.identity(),
                (client, target) -> {
                    calledModels.add(target.id());
                    return "response from " + target.id();
                });

        assertThat(response).isEqualTo("response from backup");
        assertThat(calledModels).containsExactly("backup");
        assertThat(healthStore.isUnavailable("primary")).isTrue();
    }

    private static ModelHealthStore newHealthStore() {
        AIModelProperties properties = new AIModelProperties();
        properties.getSelection().setFailureThreshold(1);
        properties.getSelection().setOpenDurationMs(60_000L);
        return new ModelHealthStore(properties);
    }

    private static ModelTarget target(String id) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider("noop");
        candidate.setModel(id + "-model");
        return new ModelTarget(id, candidate, new AIModelProperties.ProviderConfig());
    }
}
