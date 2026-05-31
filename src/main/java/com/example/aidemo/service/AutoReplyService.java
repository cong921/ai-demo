package com.example.aidemo.service;

import com.example.aidemo.dto.ReplySuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoReplyService {

    private final ChatClient.Builder chatClientBuilder;
    private final KnowledgeBaseService knowledgeBaseService;

    public ReplySuggestion suggestReply(String hrMessage) {
        String relevantInfo = knowledgeBaseService.retrieveRelevantInfo(hrMessage);

        String systemPrompt;
        boolean hasKnowledge = relevantInfo != null && !relevantInfo.isBlank();

        if (hasKnowledge) {
            systemPrompt = """
                    你是一位专业的求职沟通顾问。请根据以下个人信息，回答 HR 的问题。
                    要求：
                    1. 语气自然、专业、礼貌
                    2. 回答简洁，不要太长（50字以内）
                    3. 不要编造个人信息中没有的内容
                    4. 如果信息不足，可以委婉表示需要进一步沟通
                    5. 用第一人称"我"来回答

                    个人信息：
                    %s
                    """.formatted(relevantInfo);
        } else {
            systemPrompt = """
                    你是一位专业的求职沟通顾问。请回答 HR 的问题。
                    要求：
                    1. 语气自然、专业、礼貌
                    2. 回答简洁，不要太长（50字以内）
                    3. 用第一人称"我"来回答
                    4. 如果不确定如何回答，可以委婉表示需要进一步沟通
                    """;
        }

        try {
            String reply = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(hrMessage)
                    .call()
                    .content();

            return ReplySuggestion.builder()
                    .matched(true)
                    .questionType(extractQuestionType(hrMessage))
                    .suggestedReply(reply)
                    .confidence(hasKnowledge ? 0.9 : 0.7)
                    .build();
        } catch (Exception e) {
            log.error("AI 生成回复失败", e);
            return ReplySuggestion.builder()
                    .matched(false)
                    .suggestedReply("生成回复失败，请稍后重试")
                    .build();
        }
    }

    private String extractQuestionType(String hrMessage) {
        String lower = hrMessage.toLowerCase();
        if (lower.contains("离职") || lower.contains("辞职") || lower.contains("离开") || lower.contains("跳槽")) {
            return "离职原因";
        }
        if (lower.contains("到岗") || lower.contains("入职") || lower.contains("报道") || lower.contains("时间")) {
            return "到岗时间";
        }
        if (lower.contains("学校") || lower.contains("专业") || lower.contains("学历") || lower.contains("毕业") || lower.contains("大学")) {
            return "教育背景";
        }
        if (lower.contains("薪资") || lower.contains("工资") || lower.contains("待遇") || lower.contains("薪酬") || lower.contains("薪水")) {
            return "期望薪资";
        }
        if (lower.contains("在职") || lower.contains("状态") || lower.contains("目前")) {
            return "工作状态";
        }
        if (lower.contains("城市") || lower.contains("地点") || lower.contains("位置")) {
            return "期望城市";
        }
        if (lower.contains("年限") || lower.contains("经验")) {
            return "工作经验";
        }
        return "其他";
    }
}
