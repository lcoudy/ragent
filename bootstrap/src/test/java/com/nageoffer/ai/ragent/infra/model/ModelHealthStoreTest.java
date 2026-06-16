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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHealthStoreTest {

    @Test
    void shouldOpenCircuitOnlyAfterFailureThreshold() {
        ModelHealthStore healthStore = newHealthStore(2, 60_000L);

        healthStore.markFailure("qwen-plus");

        assertThat(healthStore.isUnavailable("qwen-plus")).isFalse();
        assertThat(healthStore.allowCall("qwen-plus")).isTrue();

        healthStore.markFailure("qwen-plus");

        assertThat(healthStore.isUnavailable("qwen-plus")).isTrue();
        assertThat(healthStore.allowCall("qwen-plus")).isFalse();
    }

    @Test
    void shouldAllowOnlyOneHalfOpenProbeAfterOpenWindowExpires() throws InterruptedException {
        ModelHealthStore healthStore = newHealthStore(1, 1L);
        healthStore.markFailure("qwen-plus");

        Thread.sleep(5L);

        assertThat(healthStore.allowCall("qwen-plus")).isTrue();
        assertThat(healthStore.isUnavailable("qwen-plus")).isTrue();
        assertThat(healthStore.allowCall("qwen-plus")).isFalse();
    }

    @Test
    void shouldCloseCircuitAfterHalfOpenSuccess() throws InterruptedException {
        ModelHealthStore healthStore = newHealthStore(1, 1L);
        healthStore.markFailure("qwen-plus");
        Thread.sleep(5L);
        assertThat(healthStore.allowCall("qwen-plus")).isTrue();

        healthStore.markSuccess("qwen-plus");

        assertThat(healthStore.isUnavailable("qwen-plus")).isFalse();
        assertThat(healthStore.allowCall("qwen-plus")).isTrue();
    }

    @Test
    void shouldReopenCircuitWhenHalfOpenProbeFails() throws InterruptedException {
        ModelHealthStore healthStore = newHealthStore(1, 60_000L);
        healthStore.markFailure("qwen-plus");

        Thread.sleep(5L);
        assertThat(healthStore.allowCall("qwen-plus")).isFalse();

        healthStore = newHealthStore(1, 1L);
        healthStore.markFailure("qwen-plus");
        Thread.sleep(5L);
        assertThat(healthStore.allowCall("qwen-plus")).isTrue();

        healthStore.markFailure("qwen-plus");

        assertThat(healthStore.isUnavailable("qwen-plus")).isTrue();
        assertThat(healthStore.allowCall("qwen-plus")).isFalse();
    }

    @Test
    void shouldRejectNullModelIdWithoutChangingOtherModels() {
        ModelHealthStore healthStore = newHealthStore(1, 60_000L);

        assertThat(healthStore.allowCall(null)).isFalse();
        healthStore.markFailure(null);
        healthStore.markSuccess(null);

        assertThat(healthStore.allowCall("backup")).isTrue();
        assertThat(healthStore.isUnavailable("backup")).isFalse();
    }

    private static ModelHealthStore newHealthStore(int failureThreshold, long openDurationMs) {
        AIModelProperties properties = new AIModelProperties();
        properties.getSelection().setFailureThreshold(failureThreshold);
        properties.getSelection().setOpenDurationMs(openDurationMs);
        return new ModelHealthStore(properties);
    }
}
