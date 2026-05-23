package com.example.aidemo.service;

import com.example.aidemo.entity.ChatHistory;
import com.example.aidemo.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final ChatMemory chatMemory;
    private final ChatHistoryRepository chatHistoryRepository;

    @Transactional
    public String chat(String conversationId, String userMessage) {
        chatHistoryRepository.save(ChatHistory.builder()
                .conversationId(conversationId)
                .role("user")
                .content(userMessage)
                .build());

        ChatClient chatClient = chatClientBuilder
                .defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
                .build();

        String response = chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(PromptChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .call()
                .content();

        chatHistoryRepository.save(ChatHistory.builder()
                .conversationId(conversationId)
                .role("assistant")
                .content(response)
                .build());

        return response;
    }

    @Transactional(readOnly = true)
    public List<ChatHistory> getChatHistory(String conversationId) {
        return chatHistoryRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public void clearChatHistory(String conversationId) {
        chatHistoryRepository.deleteByConversationId(conversationId);
        chatMemory.clear(conversationId);
    }
}
