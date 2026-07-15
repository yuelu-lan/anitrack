package com.anitrack.starter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RagChatReq {
    @NotBlank
    private String message;
}
