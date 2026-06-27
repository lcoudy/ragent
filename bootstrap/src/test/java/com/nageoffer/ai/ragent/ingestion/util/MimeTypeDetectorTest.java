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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MimeTypeDetectorTest {

    @Test
    void shouldReturnNullForEmptyBytes() {
        assertThat(MimeTypeDetector.detect(null, "report.pdf")).isNull();
        assertThat(MimeTypeDetector.detect(new byte[0], "report.pdf")).isNull();
    }

    @Test
    void shouldDetectPdfFromMagicBytesAndFileName() {
        byte[] bytes = "%PDF-1.4\n% test pdf".getBytes(StandardCharsets.UTF_8);

        String mimeType = MimeTypeDetector.detect(bytes, "report.pdf");

        assertThat(mimeType).isEqualTo("application/pdf");
    }

    @Test
    void shouldDetectPlainTextFile() {
        byte[] bytes = "plain text knowledge document".getBytes(StandardCharsets.UTF_8);

        String mimeType = MimeTypeDetector.detect(bytes, "notes.txt");

        assertThat(mimeType).isEqualTo("text/plain");
    }

    @Test
    void shouldDetectContentWhenFileNameIsMissing() {
        byte[] bytes = "%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.UTF_8);

        String mimeType = MimeTypeDetector.detect(bytes, null);

        assertThat(mimeType).isEqualTo("application/pdf");
    }
}
