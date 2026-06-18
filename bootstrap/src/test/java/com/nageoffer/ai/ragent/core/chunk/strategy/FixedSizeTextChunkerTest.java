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

package com.nageoffer.ai.ragent.core.chunk.strategy;

import com.nageoffer.ai.ragent.core.chunk.FixedSizeOptions;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSizeTextChunkerTest {

    private final FixedSizeTextChunker chunker = new FixedSizeTextChunker();

    @Test
    void shouldReturnEmptyListForBlankText() {
        assertThat(chunker.chunk(null, new FixedSizeOptions(10, 2))).isEmpty();
        assertThat(chunker.chunk("   \n\t", new FixedSizeOptions(10, 2))).isEmpty();
    }

    @Test
    void shouldKeepShortTextAsSingleChunk() {
        List<VectorChunk> chunks = chunker.chunk("short text", new FixedSizeOptions(100, 10));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getIndex()).isZero();
        assertThat(chunks.get(0).getContent()).isEqualTo("short text");
        assertThat(chunks.get(0).getChunkId()).isNotBlank();
    }

    @Test
    void shouldCreateOverlappedChunksWithoutStalling() {
        List<VectorChunk> chunks = chunker.chunk("abcdefghi", new FixedSizeOptions(4, 2));

        assertThat(chunks)
                .extracting(VectorChunk::getContent)
                .containsExactly("abcd", "cdef", "efgh", "ghi");
        assertThat(chunks)
                .extracting(VectorChunk::getIndex)
                .containsExactly(0, 1, 2, 3);
    }

    @Test
    void shouldSplitLongParagraphIntoContinuousChunks() {
        List<VectorChunk> chunks = chunker.chunk("abcdefghijklmnopqrstuvwxyz", new FixedSizeOptions(10, 0));

        assertThat(chunks)
                .extracting(VectorChunk::getContent)
                .containsExactly("abcdefghij", "klmnopqrst", "uvwxyz");
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.getContent()).isNotBlank());
    }

    @Test
    void shouldReturnWholeTextWhenChunkSizeIsDisabled() {
        List<VectorChunk> chunks = chunker.chunk("keep entire document", new FixedSizeOptions(-1, 5));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getIndex()).isZero();
        assertThat(chunks.get(0).getContent()).isEqualTo("keep entire document");
    }
}
