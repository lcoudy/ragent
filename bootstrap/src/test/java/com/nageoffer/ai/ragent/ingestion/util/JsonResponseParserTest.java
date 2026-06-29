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

package com.nageoffer.ai.ragent.ingestion.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonResponseParserTest {

    @Test
    void shouldParseStringListFromMarkdownCodeFence() {
        String raw = """
                ```json
                ["chunk", "summary"]
                ```
                """;

        assertThat(JsonResponseParser.parseStringList(raw))
                .containsExactly("chunk", "summary");
    }

    @Test
    void shouldExtractObjectFromWrappedText() {
        String raw = "模型输出如下：{\"ok\":true,\"count\":2,\"name\":\"pipeline\"}。请查收。";

        Map<String, Object> parsed = JsonResponseParser.parseObject(raw);

        assertThat(parsed)
                .containsEntry("ok", true)
                .containsEntry("count", 2.0)
                .containsEntry("name", "pipeline");
    }

    @Test
    void shouldExtractArrayFromWrappedText() {
        String raw = "result: [\"fetcher\", \"parser\", \"chunker\"] done";

        assertThat(JsonResponseParser.parseStringList(raw))
                .containsExactly("fetcher", "parser", "chunker");
    }

    @Test
    void shouldReturnEmptyValuesForInvalidOrWrongTypeInput() {
        assertThat(JsonResponseParser.parseStringList("not json")).isEmpty();
        assertThat(JsonResponseParser.parseStringList("{\"value\":\"not array\"}")).isEmpty();
        assertThat(JsonResponseParser.parseStringList(" ")).isEmpty();

        assertThat(JsonResponseParser.parseObject("[\"not\", \"object\"]")).isEmpty();
        assertThat(JsonResponseParser.parseObject("{broken")).isEmpty();
        assertThat(JsonResponseParser.parseObject(null)).isEmpty();
    }
}
