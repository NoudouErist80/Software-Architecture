package com.vibe.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TokenEarnType {
    WATCH_VIDEO("Watched a video"),
    SEND_MESSAGE("Sent a message"),
    REACT("Reacted to a post"),
    JOIN_ROOM("Joined a room"),
    HOST_ROOM("Hosted a room"),
    REFER_FRIEND("Referred a friend"),
    DAILY_STREAK("Daily streak bonus"),
    POST_CREATED("Created a post"),
    VIRAL_POST("Post went viral"),
    CASHOUT("Token cashout");

    private final String description;
}
