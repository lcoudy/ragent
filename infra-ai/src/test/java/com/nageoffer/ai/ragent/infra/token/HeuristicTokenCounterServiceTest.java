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

package com.nageoffer.ai.ragent.infra.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicTokenCounterServiceTest {

    private final HeuristicTokenCounterService tokenCounter = new HeuristicTokenCounterService();

    @Test
    void shouldReturnZeroForBlankText() {
        assertThat(tokenCounter.countTokens(null)).isZero();
        assertThat(tokenCounter.countTokens("")).isZero();
        assertThat(tokenCounter.countTokens(" \n\t ")).isZero();
    }

    @Test
    void shouldEstimateAsciiByFourCharactersPerToken() {
        assertThat(tokenCounter.countTokens("abcd")).isEqualTo(1);
        assertThat(tokenCounter.countTokens("abcde")).isEqualTo(2);
        assertThat(tokenCounter.countTokens("hello world")).isEqualTo(3);
    }

    @Test
    void shouldCountCjkCharactersAsTokens() {
        assertThat(tokenCounter.countTokens("知识库")).isEqualTo(3);
        assertThat(tokenCounter.countTokens("こんにちは")).isEqualTo(5);
    }

    @Test
    void shouldEstimateOtherCharactersByTwoCharactersPerToken() {
        assertThat(tokenCounter.countTokens("🙂")).isEqualTo(1);
        assertThat(tokenCounter.countTokens("🙂🙂🙂")).isEqualTo(3);
    }

    @Test
    void shouldCombineAsciiCjkAndOtherCharacters() {
        assertThat(tokenCounter.countTokens("RAG 知识🙂")).isEqualTo(4);
    }

    @Test
    void shouldReturnAtLeastOneForNonWhitespaceText() {
        assertThat(tokenCounter.countTokens("a")).isEqualTo(1);
    }
}
