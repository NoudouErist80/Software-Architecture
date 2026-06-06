package com.vibe.feedservice.model.request;

import com.vibe.feedservice.model.entity.enums.MoodTag;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetMoodRequest {
    @NotNull
    private MoodTag mood;
}
