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

package com.nageoffer.ai.ragent.rag.rewrite;

import com.nageoffer.ai.ragent.rag.core.rewrite.QueryTermMappingUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryTermMappingUtilTest {

    @Test
    void shouldReturnOriginalTextForBlankInputOrSource() {
        assertThat(QueryTermMappingUtil.applyMapping(null, "阿里", "阿里巴巴")).isNull();
        assertThat(QueryTermMappingUtil.applyMapping("", "阿里", "阿里巴巴")).isEmpty();
        assertThat(QueryTermMappingUtil.applyMapping("阿里使用钉钉", "", "阿里巴巴")).isEqualTo("阿里使用钉钉");
        assertThat(QueryTermMappingUtil.applyMapping("阿里使用钉钉", null, "阿里巴巴")).isEqualTo("阿里使用钉钉");
    }

    @Test
    void shouldKeepTextWhenSourceTermDoesNotMatch() {
        String text = "飞书审批流程在哪里配置？";

        String normalized = QueryTermMappingUtil.applyMapping(text, "钉钉", "DingTalk");

        assertThat(normalized).isEqualTo(text);
    }

    @Test
    void shouldApplyMultipleHitsInOneText() {
        String normalized = QueryTermMappingUtil.applyMapping(
                "阿里员工如何在阿里内部系统申请权限？",
                "阿里",
                "阿里巴巴");

        assertThat(normalized).isEqualTo("阿里巴巴员工如何在阿里巴巴内部系统申请权限？");
    }

    @Test
    void shouldNotDuplicateTargetWhenTextAlreadyStartsWithTarget() {
        String normalized = QueryTermMappingUtil.applyMapping(
                "平安保司的平安流程在哪里？",
                "平安",
                "平安保司");

        assertThat(normalized).isEqualTo("平安保司的平安保司流程在哪里？");
    }

    @Test
    void shouldApplyMappingsSequentiallyForDifferentTerms() {
        String text = "阿里使用钉钉审批";

        String normalized = QueryTermMappingUtil.applyMapping(text, "阿里", "阿里巴巴");
        normalized = QueryTermMappingUtil.applyMapping(normalized, "钉钉", "DingTalk");

        assertThat(normalized).isEqualTo("阿里巴巴使用DingTalk审批");
    }
}
