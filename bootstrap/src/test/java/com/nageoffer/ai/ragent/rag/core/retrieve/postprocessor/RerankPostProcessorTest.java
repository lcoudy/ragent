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
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RerankPostProcessorTest {

    @Mock
    private RerankService rerankService;

    private RerankPostProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new RerankPostProcessor(rerankService);
    }

    @Test
    void shouldSkipRerankWhenChunksAreEmpty() {
        SearchContext context = SearchContext.builder()
                .originalQuestion("raw question")
                .topK(3)
                .build();

        List<RetrievedChunk> processed = processor.process(List.of(), List.of(), context);

        assertThat(processed).isEmpty();
        verifyNoInteractions(rerankService);
    }

    @Test
    void shouldPassRewrittenQuestionAndTopKToRerankService() {
        List<RetrievedChunk> chunks = List.of(
                chunk("chunk-1", "first", 0.42F),
                chunk("chunk-2", "second", 0.91F));
        List<RetrievedChunk> ranked = List.of(chunks.get(1), chunks.get(0));
        SearchContext context = SearchContext.builder()
                .originalQuestion("raw question")
                .rewrittenQuestion("rewritten question")
                .topK(2)
                .build();
        when(rerankService.rerank("rewritten question", chunks, 2)).thenReturn(ranked);

        List<RetrievedChunk> processed = processor.process(chunks, List.of(), context);

        assertThat(processed).containsExactly(chunks.get(1), chunks.get(0));
        verify(rerankService).rerank("rewritten question", chunks, 2);
    }

    @Test
    void shouldUseOriginalQuestionWhenRewriteIsMissing() {
        List<RetrievedChunk> chunks = List.of(chunk("chunk-1", "answer", 0.7F));
        SearchContext context = SearchContext.builder()
                .originalQuestion("raw question")
                .topK(1)
                .build();
        when(rerankService.rerank("raw question", chunks, 1)).thenReturn(chunks);

        List<RetrievedChunk> processed = processor.process(chunks, List.of(), context);

        assertThat(processed).containsExactlyElementsOf(chunks);
        verify(rerankService).rerank("raw question", chunks, 1);
    }

    private RetrievedChunk chunk(String id, String text, Float score) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .score(score)
                .build();
    }
}
