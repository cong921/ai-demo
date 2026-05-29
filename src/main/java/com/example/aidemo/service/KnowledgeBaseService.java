package com.example.aidemo.service;

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
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseService {

    @Value("${app.auto-reply.knowledge-base.profile-path:classpath:knowledge/profile.md}")
    private String profilePath;

    private final ResourceLoader resourceLoader;
    private String profileContent;

    public KnowledgeBaseService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
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
        if (profileContent == null || profileContent.isBlank()) {
            return "";
        }

        String[] sections = profileContent.split("(?m)^## ");
        String bestMatch = "";
        int bestScore = -1;

        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            int score = calculateRelevance(section, hrMessage);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = section;
            }
        }

        return bestScore > 0 ? "## " + bestMatch.trim() : profileContent;
    }

    private int calculateRelevance(String section, String hrMessage) {
        String lowerSection = section.toLowerCase();
        String lowerMsg = hrMessage.toLowerCase();

        String[][] keywordGroups = {
            {"离职", "离开", "为啥走", "为什么走", "辞职", "跳槽"},
            {"到岗", "入职", "什么时候", "多久", "时间", "报道"},
            {"学校", "专业", "学历", "毕业", "大学", "本科", "硕士"},
            {"薪资", "工资", "期望", "多少钱", "待遇", "薪酬", "薪水"},
            {"在职", "离职", "状态", "目前", "现在"},
            {"城市", "地点", "位置", "哪里", "地区"},
            {"年限", "经验", "几年", "工作多久"}
        };

        int score = 0;
        for (String[] group : keywordGroups) {
            boolean sectionHas = Arrays.stream(group).anyMatch(lowerSection::contains);
            boolean msgHas = Arrays.stream(group).anyMatch(lowerMsg::contains);
            if (sectionHas && msgHas) {
                score += 10;
            }
        }

        String[] msgWords = lowerMsg.split("\\s+");
        for (String word : msgWords) {
            if (word.length() > 1 && lowerSection.contains(word)) {
                score += 1;
            }
        }

        return score;
    }

    private void loadProfile() {
        try {
            Resource resource = resourceLoader.getResource(profilePath);
            if (!resource.exists()) {
                log.warn("知识库文件不存在: {}，尝试加载模板", profilePath);
                resource = resourceLoader.getResource("classpath:knowledge/profile-template.md");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                this.profileContent = reader.lines().collect(Collectors.joining("\n"));
            }

            log.info("知识库加载成功，内容长度: {} 字符", profileContent.length());
        } catch (IOException e) {
            log.error("加载知识库失败", e);
            this.profileContent = "";
        }
    }
}
