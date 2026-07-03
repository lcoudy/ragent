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

package com.nageoffer.ai.ragent.core.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCleanupUtilTest {

    @Test
    void shouldReturnEmptyStringForNullOrEmptyText() {
        assertThat(TextCleanupUtil.cleanup(null)).isEmpty();
        assertThat(TextCleanupUtil.cleanup("")).isEmpty();
    }

    @Test
    void shouldRemoveBomTrailingSpacesAndExcessiveBlankLines() {
        String text = "\uFEFF  title  \nline with spaces   \n\n\n\nend\t\n";

        String cleaned = TextCleanupUtil.cleanup(text);

        assertThat(cleaned).isEqualTo("title\nline with spaces\n\nend");
    }

    @Test
    void shouldRespectDisabledBomRemoval() {
        String cleaned = TextCleanupUtil.cleanup("\uFEFFtitle", false, true, true, 2);

        assertThat(cleaned).startsWith("\uFEFF");
    }

    @Test
    void shouldRespectDisabledTrailingSpaceTrimming() {
        String cleaned = TextCleanupUtil.cleanup("line   \nnext", true, false, true, 2);

        assertThat(cleaned).isEqualTo("line   \nnext");
    }

    @Test
    void shouldUseCustomMaximumEmptyLines() {
        String cleaned = TextCleanupUtil.cleanup("a\n\n\n\nb", true, true, true, 1);

        assertThat(cleaned).isEqualTo("a\nb");
    }

    @Test
    void shouldSkipEmptyLineCompressionWhenLimitIsInvalid() {
        String cleaned = TextCleanupUtil.cleanup("a\n\n\nb", true, true, true, 0);

        assertThat(cleaned).isEqualTo("a\n\n\nb");
    }
}
