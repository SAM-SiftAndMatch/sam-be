package com.sam.be.modules.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQuestion {
    private String id;
    private String type;
    private String questionText;
    private List<String> options;
    private Boolean allowCustomInput;
    private String hint;
}