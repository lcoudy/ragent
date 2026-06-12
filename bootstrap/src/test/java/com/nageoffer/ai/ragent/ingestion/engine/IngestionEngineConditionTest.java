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
import com.nageoffer.ai.ragent.ingestion.domain.context.IngestionContext;
import com.nageoffer.ai.ragent.ingestion.domain.context.NodeLog;
import com.nageoffer.ai.ragent.ingestion.domain.enums.IngestionStatus;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import com.nageoffer.ai.ragent.ingestion.domain.pipeline.PipelineDefinition;
import com.nageoffer.ai.ragent.ingestion.domain.result.NodeResult;
import com.nageoffer.ai.ragent.ingestion.node.IngestionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionEngineConditionTest {

    private ObjectMapper objectMapper;
    private RecordingNode parserNode;
    private IngestionEngine ingestionEngine;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parserNode = new RecordingNode("parser");
        ingestionEngine = new IngestionEngine(
                List.of(parserNode),
                new ConditionEvaluator(objectMapper),
                new NodeOutputExtractor());
    }

    @Test
    void shouldSkipNodeAndRecordLogWhenConditionIsFalse() {
        IngestionContext context = IngestionContext.builder()
                .rawText("invoice content")
                .build();
        PipelineDefinition pipeline = pipeline(node(false));

        IngestionContext result = ingestionEngine.execute(pipeline, context);

        assertThat(result.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(parserNode.calls).isZero();
        assertThat(result.getLogs()).hasSize(1);
        NodeLog log = result.getLogs().get(0);
        assertThat(log.getNodeId()).isEqualTo("parse");
        assertThat(log.getNodeType()).isEqualTo("parser");
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getMessage()).isEqualTo("Skipped: 条件未满足");
        assertThat(log.getDurationMs()).isZero();
        assertThat(log.getOutput()).containsEntry("rawText", "invoice content");
    }

    @Test
    void shouldExecuteNodeAndRecordLogWhenConditionMatches() {
        IngestionContext context = IngestionContext.builder()
                .rawText("invoice content")
                .build();
        PipelineDefinition pipeline = pipeline(nodeWithRawTextContainsCondition("invoice"));

        IngestionContext result = ingestionEngine.execute(pipeline, context);

        assertThat(result.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(parserNode.calls).isEqualTo(1);
        assertThat(result.getLogs()).hasSize(1);
        NodeLog log = result.getLogs().get(0);
        assertThat(log.getNodeId()).isEqualTo("parse");
        assertThat(log.isSuccess()).isTrue();
        assertThat(log.getMessage()).isEqualTo("ran parser");
        assertThat(log.getOutput()).containsEntry("rawText", "invoice content");
    }

    private PipelineDefinition pipeline(NodeConfig node) {
        return PipelineDefinition.builder()
                .id("condition-test-pipeline")
                .name("Condition Test Pipeline")
                .nodes(List.of(node))
                .build();
    }

    private NodeConfig node(boolean condition) {
        return NodeConfig.builder()
                .nodeId("parse")
                .nodeType("parser")
                .condition(objectMapper.valueToTree(condition))
                .build();
    }

    private NodeConfig nodeWithRawTextContainsCondition(String value) {
        return NodeConfig.builder()
                .nodeId("parse")
                .nodeType("parser")
                .condition(objectMapper.createObjectNode()
                        .put("field", "rawText")
                        .put("operator", "contains")
                        .put("value", value))
                .build();
    }

    private static final class RecordingNode implements IngestionNode {

        private final String nodeType;
        private int calls;

        private RecordingNode(String nodeType) {
            this.nodeType = nodeType;
        }

        @Override
        public String getNodeType() {
            return nodeType;
        }

        @Override
        public NodeResult execute(IngestionContext context, NodeConfig config) {
            calls++;
            return NodeResult.ok("ran " + nodeType);
        }
    }
}
