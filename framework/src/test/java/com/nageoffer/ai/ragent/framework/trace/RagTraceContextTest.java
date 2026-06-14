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

package com.nageoffer.ai.ragent.framework.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagTraceContextTest {

    @AfterEach
    void tearDown() {
        RagTraceContext.clear();
    }

    @Test
    void shouldStoreTraceAndTaskIds() {
        RagTraceContext.setTraceId("trace-1");
        RagTraceContext.setTaskId("task-1");

        assertThat(RagTraceContext.getTraceId()).isEqualTo("trace-1");
        assertThat(RagTraceContext.getTaskId()).isEqualTo("task-1");
        assertThat(RagTraceContext.depth()).isZero();
        assertThat(RagTraceContext.currentNodeId()).isNull();
    }

    @Test
    void shouldTrackNestedNodeStack() {
        RagTraceContext.pushNode("root-node");
        RagTraceContext.pushNode("child-node");

        assertThat(RagTraceContext.depth()).isEqualTo(2);
        assertThat(RagTraceContext.currentNodeId()).isEqualTo("child-node");

        RagTraceContext.popNode();

        assertThat(RagTraceContext.depth()).isEqualTo(1);
        assertThat(RagTraceContext.currentNodeId()).isEqualTo("root-node");

        RagTraceContext.popNode();

        assertThat(RagTraceContext.depth()).isZero();
        assertThat(RagTraceContext.currentNodeId()).isNull();
    }

    @Test
    void shouldIgnorePopWhenNodeStackIsEmpty() {
        RagTraceContext.popNode();

        assertThat(RagTraceContext.depth()).isZero();
        assertThat(RagTraceContext.currentNodeId()).isNull();
    }

    @Test
    void shouldClearTraceIdsAndNodeStackTogether() {
        RagTraceContext.setTraceId("trace-1");
        RagTraceContext.setTaskId("task-1");
        RagTraceContext.pushNode("node-1");

        RagTraceContext.clear();

        assertThat(RagTraceContext.getTraceId()).isNull();
        assertThat(RagTraceContext.getTaskId()).isNull();
        assertThat(RagTraceContext.depth()).isZero();
        assertThat(RagTraceContext.currentNodeId()).isNull();
    }
}
