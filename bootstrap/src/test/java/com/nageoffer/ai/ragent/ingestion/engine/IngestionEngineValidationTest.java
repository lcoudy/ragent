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

package com.nageoffer.ai.ragent.ingestion.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.ingestion.domain.context.IngestionContext;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.PipelineDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestionEngineValidationTest {

    private IngestionEngine ingestionEngine;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ingestionEngine = new IngestionEngine(
                List.of(),
                new ConditionEvaluator(objectMapper),
                new NodeOutputExtractor());
    }

    @Test
    void shouldRejectPipelineWithCycle() {
        PipelineDefinition pipeline = pipeline(
                node("fetch", "fetcher", "parse"),
                node("parse", "parser", "fetch"));

        ClientException exception = assertThrows(
                ClientException.class,
                () -> ingestionEngine.execute(pipeline, new IngestionContext()));

        assertThat(exception.getErrorMessage())
                .contains("流水线存在环")
                .contains("fetch");
    }

    @Test
    void shouldRejectPipelineReferencingMissingNextNode() {
        PipelineDefinition pipeline = pipeline(
                node("fetch", "fetcher", "missing-node"));

        ClientException exception = assertThrows(
                ClientException.class,
                () -> ingestionEngine.execute(pipeline, new IngestionContext()));

        assertThat(exception.getErrorMessage())
                .contains("找不到下一个节点: missing-node")
                .contains("被节点 fetch 引用");
    }

    @Test
    void shouldRejectPipelineWithoutStartNode() {
        PipelineDefinition pipeline = pipeline();

        ClientException exception = assertThrows(
                ClientException.class,
                () -> ingestionEngine.execute(pipeline, new IngestionContext()));

        assertThat(exception.getErrorMessage())
                .contains("流水线未找到起始节点");
    }

    @Test
    void shouldInitializeLogsBeforeValidationFailure() {
        IngestionContext context = new IngestionContext();
        PipelineDefinition pipeline = pipeline(
                node("fetch", "fetcher", "missing-node"));

        assertThrows(
                ClientException.class,
                () -> ingestionEngine.execute(pipeline, context));

        assertThat(context.getLogs()).isEmpty();
    }

    private PipelineDefinition pipeline(NodeConfig... nodes) {
        return PipelineDefinition.builder()
                .id("validation-test-pipeline")
                .name("Validation Test Pipeline")
                .nodes(List.of(nodes))
                .build();
    }

    private NodeConfig node(String nodeId, String nodeType, String nextNodeId) {
        return NodeConfig.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .nextNodeId(nextNodeId)
                .build();
    }
}
