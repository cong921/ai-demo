package com.example.aidemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseService {

    @Value("${app.auto-reply.knowledge-base.profile-path:classpath:knowledge/profile.json}")
    private String profilePath;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private Map<String, String> knowledgeMap;

    public KnowledgeBaseService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadProfile();
    }

    public void reload() {
        loadProfile();
        log.info("知识库已重新加载");
    }

    public String retrieveRelevantInfo(String hrMessage) {
        if (knowledgeMap == null || knowledgeMap.isEmpty()) {
            return "";
        }

        List<Map.Entry<String, String>> matched = new ArrayList<>();

        for (Map.Entry<String, String> entry : knowledgeMap.entrySet()) {
            int score = calculateRelevance(entry.getKey(), entry.getValue(), hrMessage);
            if (score > 0) {
                matched.add(entry);
            }
        }

        if (matched.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : matched) {
            sb.append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    private int calculateRelevance(String key, String value, String hrMessage) {
        String lowerKey = key.toLowerCase();
        String lowerValue = value.toLowerCase();
        String lowerMsg = hrMessage.toLowerCase();

        String[][] keywordGroups = {
                {"离职", "离开", "为啥走", "为什么走", "辞职", "跳槽"},
                {"到岗", "入职", "什么时候", "多久", "时间", "报道"},
                {"学校", "专业", "学历", "毕业", "大学", "本科", "硕士"},
                {"薪资", "工资", "期望", "多少钱", "待遇", "薪酬", "薪水"},
                {"在职", "状态", "目前", "现在"},
                {"城市", "地点", "位置", "哪里", "地区"},
                {"年限", "经验", "几年", "工作多久"},
                {"双证", "毕业证", "学位证", "证件"},
                {"外包", "外派"},
                {"面试", "线下面试"},
                {"学信网", "统招"}
        };

        int score = 0;
        for (String[] group : keywordGroups) {
            boolean keyHas = Arrays.stream(group).anyMatch(lowerKey::contains);
            boolean valueHas = Arrays.stream(group).anyMatch(lowerValue::contains);
            boolean msgHas = Arrays.stream(group).anyMatch(lowerMsg::contains);
            if ((keyHas || valueHas) && msgHas) {
                score += 10;
            }
        }

        String[] msgWords = lowerMsg.split("\\s+");
        for (String word : msgWords) {
            if (word.length() > 1 && (lowerKey.contains(word) || lowerValue.contains(word))) {
                score += 1;
            }
        }

        return score;
    }

    private void loadProfile() {
        try {
            Resource resource = resourceLoader.getResource(profilePath);
            if (!resource.exists()) {
                log.warn("知识库文件不存在: {}", profilePath);
                this.knowledgeMap = Collections.emptyMap();
                return;
            }

            String json;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                json = reader.lines().collect(Collectors.joining("\n"));
            }

            JsonNode root = objectMapper.readTree(json);
            this.knowledgeMap = new LinkedHashMap<>();
            flattenJson(root, "", knowledgeMap);

            log.info("知识库加载成功，共 {} 条记录", knowledgeMap.size());
        } catch (IOException e) {
            log.error("加载知识库失败", e);
            this.knowledgeMap = Collections.emptyMap();
        }
    }

    private void flattenJson(JsonNode node, String prefix, Map<String, String> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flattenJson(field.getValue(), key, result);
            }
        } else {
            result.put(prefix, node.asText());
        }
    }
}
