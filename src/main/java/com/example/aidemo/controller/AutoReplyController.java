package com.example.aidemo.controller;

import com.example.aidemo.dto.AutoReplyRequest;
import com.example.aidemo.dto.ReplySuggestion;
import com.example.aidemo.service.AutoReplyService;
import com.example.aidemo.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auto-reply")
@RequiredArgsConstructor
public class AutoReplyController {

    private final AutoReplyService autoReplyService;
    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/suggest")
    public ResponseEntity<ReplySuggestion> suggestReply(@RequestBody AutoReplyRequest request) {
        ReplySuggestion suggestion = autoReplyService.suggestReply(request.getHrMessage());
        return ResponseEntity.ok(suggestion);
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadKnowledgeBase() {
        knowledgeBaseService.reload();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "知识库已重新加载"
        ));
    }
}
