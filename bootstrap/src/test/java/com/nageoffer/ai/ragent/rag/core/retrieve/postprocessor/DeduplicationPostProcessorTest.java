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

package com.nageoffer.ai.ragent.rag.core.retrieve.postprocessor;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeduplicationPostProcessorTest {

    private final DeduplicationPostProcessor processor = new DeduplicationPostProcessor();

    @Test
    void shouldKeepHighestScoreForDuplicateChunkId() {
        RetrievedChunk lowerScore = chunk("chunk-1", "same chunk", 0.42F);
        RetrievedChunk higherScore = chunk("chunk-1", "same chunk from another channel", 0.91F);
        RetrievedChunk unique = chunk("chunk-2", "unique chunk", 0.73F);

        List<RetrievedChunk> processed = processor.process(
                List.of(),
                List.of(
                        channel(SearchChannelType.INTENT_DIRECTED, lowerScore, unique),
                        channel(SearchChannelType.KEYWORD_ES, higherScore)),
                null);

        assertThat(processed)
                .extracting(RetrievedChunk::getId)
                .containsExactly("chunk-1", "chunk-2");
        assertThat(processed.get(0).getScore()).isEqualTo(0.91F);
        assertThat(processed.get(0).getText()).isEqualTo("same chunk from another channel");
    }

    @Test
    void shouldDeduplicateSameTextAcrossDifferentChannelsWhenIdIsMissing() {
        RetrievedChunk globalMatch = chunk(null, "billing refund policy", 0.66F);
        RetrievedChunk intentMatch = chunk(null, "billing refund policy", 0.82F);
        RetrievedChunk keywordOnly = chunk(null, "billing invoice address", 0.57F);

        List<RetrievedChunk> processed = processor.process(
                List.of(),
                List.of(
                        channel(SearchChannelType.VECTOR_GLOBAL, globalMatch),
                        channel(SearchChannelType.INTENT_DIRECTED, intentMatch),
                        channel(SearchChannelType.KEYWORD_ES, keywordOnly)),
                null);

        assertThat(processed)
                .extracting(RetrievedChunk::getText)
                .containsExactly("billing refund policy", "billing invoice address");
        assertThat(processed.get(0).getScore()).isEqualTo(0.82F);
    }

    @Test
    void shouldReturnEmptyListWhenChannelResultsAreEmpty() {
        List<RetrievedChunk> processed = processor.process(
                List.of(chunk("chunk-1", "ignored incoming chunk", 0.5F)),
                List.of(),
                null);

        assertThat(processed).isEmpty();
    }

    private SearchChannelResult channel(SearchChannelType type, RetrievedChunk... chunks) {
        return SearchChannelResult.builder()
                .channelType(type)
                .channelName(type.name())
                .chunks(List.of(chunks))
                .build();
    }

    private RetrievedChunk chunk(String id, String text, Float score) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .score(score)
                .build();
    }
}
