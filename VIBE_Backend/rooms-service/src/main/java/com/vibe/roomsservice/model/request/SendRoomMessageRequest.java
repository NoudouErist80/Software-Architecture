package com.vibe.roomsservice.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendRoomMessageRequest {
    @NotBlank
    @Size(max = 500)
    private String content;
    private String type = "TEXT";
}
