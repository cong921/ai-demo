package com.example.aidemo.controller;

import com.example.aidemo.dto.ChatRequest;
import com.example.aidemo.dto.ChatResponse;
import com.example.aidemo.entity.ChatHistory;
import com.example.aidemo.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        String response = chatService.chat(conversationId, request.getMessage());

        return ResponseEntity.ok(ChatResponse.builder()
                .conversationId(conversationId)
                .message(response)
                .build());
    }

    @GetMapping("/history/{conversationId}")
    public ResponseEntity<List<ChatHistory>> getChatHistory(@PathVariable String conversationId) {
        List<ChatHistory> history = chatService.getChatHistory(conversationId);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/history/{conversationId}")
    public ResponseEntity<Void> clearChatHistory(@PathVariable String conversationId) {
        chatService.clearChatHistory(conversationId);
        return ResponseEntity.noContent().build();
    }
}
