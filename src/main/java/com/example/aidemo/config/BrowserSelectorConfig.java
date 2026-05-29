package com.example.aidemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.browser-bot")
public class BrowserSelectorConfig {
    private String chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    private int pollIntervalSeconds = 3;
    private boolean headless = false;

    private String messageListSelector = ".message-list";
    private String messageItemSelector = ".message-item";
    private String otherMessageClass = "other";
    private String inputBoxSelector = ".chat-input textarea";
    private String sendButtonSelector = ".send-btn";
}
