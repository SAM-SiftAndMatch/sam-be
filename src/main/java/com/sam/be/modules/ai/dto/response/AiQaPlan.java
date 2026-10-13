package com.sam.be.modules.ai.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class AiQaPlan {
    private List<AiQaChecklistItem> checklistItems;
}
