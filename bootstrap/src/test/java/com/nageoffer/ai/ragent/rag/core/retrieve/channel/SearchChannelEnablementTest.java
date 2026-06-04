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

package com.nageoffer.ai.ragent.rag.core.retrieve.channel;

import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class SearchChannelEnablementTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void vectorGlobalShouldBeDisabledWhenConfigTurnsItOff() {
        SearchChannelProperties properties = properties();
        properties.getChannels().getVectorGlobal().setEnabled(false);

        VectorGlobalSearchChannel channel = vectorGlobalChannel(properties);

        assertThat(channel.isEnabled(context(score(IntentKind.KB, 0.2)))).isFalse();
    }

    @Test
    void vectorGlobalShouldBeEnabledWhenNoIntentScoresExist() {
        VectorGlobalSearchChannel channel = vectorGlobalChannel(properties());

        assertThat(channel.isEnabled(context(List.of(new SubQuestionIntent("question", List.of()))))).isTrue();
    }

    @Test
    void vectorGlobalShouldBeEnabledWhenMaxIntentScoreIsBelowConfidenceThreshold() {
        SearchChannelProperties properties = properties();
        properties.getChannels().getVectorGlobal().setConfidenceThreshold(0.6);

        VectorGlobalSearchChannel channel = vectorGlobalChannel(properties);

        assertThat(channel.isEnabled(context(score(IntentKind.KB, 0.59)))).isTrue();
    }

    @Test
    void vectorGlobalShouldSupplementSingleMediumConfidenceIntent() {
        SearchChannelProperties properties = properties();
        properties.getChannels().getVectorGlobal().setConfidenceThreshold(0.6);
        properties.getChannels().getVectorGlobal().setSingleIntentSupplementThreshold(0.8);

        VectorGlobalSearchChannel channel = vectorGlobalChannel(properties);

        assertThat(channel.isEnabled(context(score(IntentKind.KB, 0.7)))).isTrue();
    }

    @Test
    void vectorGlobalShouldStayDisabledForConfidentIntent() {
        SearchChannelProperties properties = properties();
        properties.getChannels().getVectorGlobal().setConfidenceThreshold(0.6);
        properties.getChannels().getVectorGlobal().setSingleIntentSupplementThreshold(0.8);

        VectorGlobalSearchChannel channel = vectorGlobalChannel(properties);

        assertThat(channel.isEnabled(context(score(IntentKind.KB, 0.91)))).isFalse();
    }

    @Test
    void intentDirectedShouldBeDisabledWhenConfigTurnsItOff() {
        SearchChannelProperties properties = properties();
        properties.getChannels().getIntentDirected().setEnabled(false);

        IntentDirectedSearchChannel channel = intentDirectedChannel(properties);

        assertThat(channel.isEnabled(context(score(IntentKind.KB, 0.9)))).isFalse();
    }

    @Test
    void intentDirectedShouldBeDisabledWhenThereAreNoIntents() {
        IntentDirectedSearchChannel channel = intentDirectedChannel(properties());

        assertThat(channel.isEnabled(context(List.of()))).isFalse();
    }

    @Test
    void intentDirectedShouldIgnoreNonKbIntents() {
        IntentDirectedSearchChannel channel = intentDirectedChannel(properties());

        assertThat(channel.isEnabled(context(score(IntentKind.MCP, 0.9)))).isFalse();
    }

    @Test
    void intentDirectedShouldRequireKbIntentScoreAtLeastMinScore() {
        SearchChannelProperties properties = properties();
        properties.getChannels().getIntentDirected().setMinIntentScore(0.4);

        IntentDirectedSearchChannel channel = intentDirectedChannel(properties);

        assertThat(channel.isEnabled(context(score(IntentKind.KB, 0.39)))).isFalse();
        assertThat(channel.isEnabled(context(score(IntentKind.KB, 0.4)))).isTrue();
    }

    private VectorGlobalSearchChannel vectorGlobalChannel(SearchChannelProperties properties) {
        return new VectorGlobalSearchChannel(null, properties, null, DIRECT_EXECUTOR);
    }

    private IntentDirectedSearchChannel intentDirectedChannel(SearchChannelProperties properties) {
        return new IntentDirectedSearchChannel(null, properties, DIRECT_EXECUTOR);
    }

    private SearchChannelProperties properties() {
        return new SearchChannelProperties();
    }

    private SearchContext context(NodeScore... scores) {
        return context(List.of(new SubQuestionIntent("question", List.of(scores))));
    }

    private SearchContext context(List<SubQuestionIntent> intents) {
        return SearchContext.builder()
                .originalQuestion("question")
                .rewrittenQuestion("rewritten question")
                .intents(intents)
                .topK(5)
                .build();
    }

    private NodeScore score(IntentKind kind, double score) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id(kind.name().toLowerCase())
                        .kind(kind)
                        .collectionName(kind == IntentKind.KB ? "kb_collection" : null)
                        .mcpToolId(kind == IntentKind.MCP ? "tool_query" : null)
                        .build())
                .score(score)
                .build();
    }
}
