package com.example.aidemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplySuggestion {

    private boolean matched;
    private String questionType;
    private String suggestedReply;
    private double confidence;
}
