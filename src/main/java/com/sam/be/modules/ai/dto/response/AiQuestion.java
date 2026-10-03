package com.sam.be.modules.ai.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
