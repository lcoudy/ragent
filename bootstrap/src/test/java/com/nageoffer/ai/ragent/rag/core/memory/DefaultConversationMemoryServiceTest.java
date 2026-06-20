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

package com.nageoffer.ai.ragent.rag.core.memory;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.rag.config.MemoryProperties;
import com.nageoffer.ai.ragent.rag.controller.vo.ConversationMessageVO;
import com.nageoffer.ai.ragent.rag.enums.ConversationMessageOrder;
import com.nageoffer.ai.ragent.rag.service.ConversationMessageService;
import com.nageoffer.ai.ragent.rag.service.ConversationService;
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
class DefaultConversationMemoryServiceTest {

    @Mock
    private ConversationMemoryStore memoryStore;

    @Mock
    private ConversationMemorySummaryService summaryService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ConversationMessageService conversationMessageService;

    private DefaultConversationMemoryService memoryService;

    @BeforeEach
    void setUp() {
        memoryService = new DefaultConversationMemoryService(memoryStore, summaryService, Runnable::run);
    }

    @Test
    void shouldReturnEmptyAndSkipDependenciesForBlankIdentifiers() {
        assertThat(memoryService.load("", "user-1")).isEmpty();
        assertThat(memoryService.append("conversation-1", " ", ChatMessage.user("hello"))).isNull();

        verifyNoInteractions(memoryStore, summaryService);
    }

    @Test
    void shouldReturnEmptyWhenHistoryIsEmptyEvenIfSummaryExists() {
        when(summaryService.loadLatestSummary("conversation-1", "user-1")).thenReturn(ChatMessage.system("summary"));
        when(memoryStore.loadHistory("conversation-1", "user-1")).thenReturn(List.of());

        List<ChatMessage> result = memoryService.load("conversation-1", "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPrependDecoratedSummaryBeforeRecentHistory() {
        ChatMessage rawSummary = ChatMessage.system("raw summary");
        ChatMessage decoratedSummary = ChatMessage.system("decorated summary");
        List<ChatMessage> history = List.of(ChatMessage.user("question"), ChatMessage.assistant("answer"));
        when(summaryService.loadLatestSummary("conversation-1", "user-1")).thenReturn(rawSummary);
        when(summaryService.decorateIfNeeded(rawSummary)).thenReturn(decoratedSummary);
        when(memoryStore.loadHistory("conversation-1", "user-1")).thenReturn(history);

        List<ChatMessage> result = memoryService.load("conversation-1", "user-1");

        assertThat(result).containsExactly(decoratedSummary, history.get(0), history.get(1));
    }

    @Test
    void shouldFallbackToEmptyHistoryWhenStoreThrows() {
        when(summaryService.loadLatestSummary("conversation-1", "user-1")).thenReturn(ChatMessage.system("summary"));
        when(memoryStore.loadHistory("conversation-1", "user-1")).thenThrow(new IllegalStateException("db down"));

        List<ChatMessage> result = memoryService.load("conversation-1", "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldAppendMessageAndTriggerSummaryCompression() {
        ChatMessage message = ChatMessage.user("question");
        when(memoryStore.append("conversation-1", "user-1", message)).thenReturn("message-1");

        String messageId = memoryService.append("conversation-1", "user-1", message);

        assertThat(messageId).isEqualTo("message-1");
        verify(summaryService).compressIfNeeded("conversation-1", "user-1", message);
    }

    @Test
    void jdbcStoreShouldLoadLimitedHistoryAndDropLeadingAssistant() {
        MemoryProperties properties = new MemoryProperties();
        properties.setHistoryKeepTurns(2);
        JdbcConversationMemoryStore jdbcStore = new JdbcConversationMemoryStore(
                conversationService,
                conversationMessageService,
                properties);
        when(conversationMessageService.listMessages(
                "conversation-1",
                "user-1",
                4,
                ConversationMessageOrder.DESC))
                .thenReturn(List.of(
                        message("assistant", "orphan answer"),
                        message("user", "question"),
                        message("assistant", "answer"),
                        message("system", "ignored system message")));

        List<ChatMessage> history = jdbcStore.loadHistory("conversation-1", "user-1");

        assertThat(history)
                .extracting(ChatMessage::getContent)
                .containsExactly("question", "answer");
        verify(conversationMessageService).listMessages(
                "conversation-1",
                "user-1",
                4,
                ConversationMessageOrder.DESC);
        verifyNoInteractions(conversationService);
    }

    private ConversationMessageVO message(String role, String content) {
        return ConversationMessageVO.builder()
                .role(role)
                .content(content)
                .build();
    }
}
