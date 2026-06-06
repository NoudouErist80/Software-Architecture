package com.vibe.messagingservice.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class CreateGroupRequest {
    @NotBlank
    private String name;
    private String description;
    private String avatarUrl;
    @NotEmpty
    private List<String> participantIds;
    /** Whether new members need admin approval to join (default: false) */
    private Boolean requiresApproval;
    /** Maximum members allowed (default: 500 for groups, 5000 for communities) */
    private int maxMembers;
}
