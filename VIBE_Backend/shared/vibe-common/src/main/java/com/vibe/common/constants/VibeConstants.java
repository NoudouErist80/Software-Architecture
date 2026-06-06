package com.vibe.common.constants;

public final class VibeConstants {
    private VibeConstants() {}

    // ── Kafka Topics ──────────────────────────────────────────────────
    public static final String TOPIC_USER_REGISTERED       = "vibe.user.registered";
    public static final String TOPIC_MESSAGE_SENT          = "vibe.message.sent";
    public static final String TOPIC_VIDEO_WATCHED         = "vibe.video.watched";
    public static final String TOPIC_POST_CREATED          = "vibe.post.created";
    public static final String TOPIC_POST_ENGAGED          = "vibe.post.engaged";
    public static final String TOPIC_ROOM_JOINED           = "vibe.room.joined";
    public static final String TOPIC_ROOM_HOSTED           = "vibe.room.hosted";
    public static final String TOPIC_FRIEND_REFERRED       = "vibe.friend.referred";
    public static final String TOPIC_TOKEN_EARNED          = "vibe.token.earned";
    public static final String TOPIC_TOKEN_REDEEMED        = "vibe.token.redeemed";
    public static final String TOPIC_NOTIFICATION_SEND     = "vibe.notification.send";
    public static final String TOPIC_AI_SUMMARY_REQUEST    = "vibe.ai.summary.request";
    public static final String TOPIC_AI_SUMMARY_RESPONSE   = "vibe.ai.summary.response";
    public static final String TOPIC_TRANSLATION_REQUEST   = "vibe.translation.request";
    public static final String TOPIC_STATUS_POSTED         = "vibe.status.posted";
    public static final String TOPIC_STATUS_VIEWED         = "vibe.status.viewed";
    public static final String TOPIC_CONTACT_ADDED         = "vibe.contact.added";

    // ── Kafka Consumer Groups ─────────────────────────────────────────
    public static final String GROUP_REWARDS_SERVICE       = "vibe-rewards-service";
    public static final String GROUP_NOTIFICATION_SERVICE  = "vibe-notification-service";
    public static final String GROUP_AI_SERVICE            = "vibe-ai-service";
    public static final String GROUP_FEED_SERVICE          = "vibe-feed-service";

    // ── Redis Key Prefixes ────────────────────────────────────────────
    public static final String CACHE_USER_PROFILE          = "vibe:user:profile:";
    public static final String CACHE_USER_FEED             = "vibe:user:feed:";
    public static final String CACHE_USER_TOKEN_BALANCE    = "vibe:token:balance:";
    public static final String CACHE_ACTIVE_ROOMS          = "vibe:rooms:active";
    public static final String CACHE_ROOM_PARTICIPANTS     = "vibe:room:participants:";
    public static final String CACHE_CHAT_SUMMARY          = "vibe:chat:summary:";
    public static final String CACHE_REFRESH_TOKEN         = "vibe:auth:refresh:";
    public static final String CACHE_DAILY_EARNINGS        = "vibe:earnings:daily:";
    public static final String CACHE_USER_CONTACTS         = "vibe:user:contacts:";
    public static final String CACHE_ONLINE_USERS          = "vibe:online:users";
    public static final String CACHE_STATUSES              = "vibe:statuses:";

    // ── Token Economy ─────────────────────────────────────────────────
    public static final int TOKEN_EARN_WATCH_VIDEO         = 2;
    public static final int TOKEN_EARN_POST_PER_100_VIEWS  = 10;
    public static final int TOKEN_EARN_SEND_MESSAGE        = 1;
    public static final int TOKEN_EARN_REACT               = 1;
    public static final int TOKEN_EARN_JOIN_ROOM_PER_10MIN = 1;
    public static final int TOKEN_EARN_HOST_ROOM_PER_PERSON= 2;
    public static final int TOKEN_EARN_REFER_FRIEND        = 50;
    public static final int TOKEN_EARN_STREAK_DAY_1        = 5;
    public static final int TOKEN_EARN_STREAK_DAY_7        = 50;
    public static final int TOKEN_EARN_STREAK_DAY_30       = 300;
    public static final int TOKEN_EARN_VIRAL_POST          = 500;
    public static final int TOKEN_EARN_POST_STATUS         = 2;
    public static final int TOKEN_DAILY_EARNING_CAP        = 150;
    public static final int TOKEN_MIN_CASHOUT              = 500;
    public static final double TOKEN_TO_XAF_RATE           = 2.0;

    // ── Viral Detection ───────────────────────────────────────────────
    /**
     * Number of views a post must reach within its first 24 hours to be
     * classified as viral and trigger the TOKEN_EARN_VIRAL_POST reward.
     * Used by FeedService.watchVideo().
     */
    public static final long VIRAL_VIEW_THRESHOLD          = 10_000L;

    // ── Security ─────────────────────────────────────────────────────
    public static final String AUTH_HEADER                 = "Authorization";
    public static final String BEARER_PREFIX               = "Bearer ";
    public static final String USER_ID_HEADER              = "X-User-Id";
    public static final String USERNAME_HEADER             = "X-Username";
    public static final String ROLE_HEADER                 = "X-User-Role";

    // ── Pagination ────────────────────────────────────────────────────
    public static final int DEFAULT_PAGE_SIZE              = 20;
    public static final int MAX_PAGE_SIZE                  = 100;

    // ── Messaging ─────────────────────────────────────────────────────
    public static final int MESSAGE_EDIT_WINDOW_MINUTES    = 15;
    public static final int MESSAGE_DELETE_WINDOW_MINUTES  = 15;
    public static final int STATUS_EXPIRY_HOURS            = 24;
    public static final int VIDEO_STATUS_MAX_SECONDS       = 120; // 2 min (vs WhatsApp 1 min)

    // ── Supported Languages — individual code constants ───────────────
    //    These are used as switch-case labels in TranslationService
    //    and SummarisationService. Each value matches the BCP-47 / VIBE
    //    language code sent from the frontend (see SUPPORTED_LANGUAGES).
    public static final String LANG_ENGLISH = "en";
    public static final String LANG_FRENCH  = "fr";
    public static final String LANG_HAUSA   = "ha";
    public static final String LANG_EWONDO  = "ewo";
    public static final String LANG_PIDGIN  = "pcm";   // Cameroonian/Nigerian Pidgin English
    public static final String LANG_YORUBA  = "yo";
    public static final String LANG_TWI     = "tw";    // Twi / Akan (Ghana)
    public static final String LANG_FANTE   = "fat";   // Fante (Ghana)
    public static final String LANG_GA      = "gaa";   // Ga (Accra, Ghana)
    public static final String LANG_LINGALA = "ln";
    public static final String LANG_BAMBARA = "bm";    // Bamanankan (Mali)

    // ── Supported languages — comma-separated list (for config / docs) ─
    //    Must stay in sync with the individual constants above.
    public static final String SUPPORTED_LANGUAGES =
            LANG_ENGLISH  + "," +
            LANG_FRENCH   + "," +
            LANG_HAUSA    + "," +
            LANG_EWONDO   + "," +
            LANG_PIDGIN   + "," +
            LANG_YORUBA   + "," +
            LANG_TWI      + "," +
            LANG_FANTE    + "," +
            LANG_GA       + "," +
            LANG_LINGALA  + "," +
            LANG_BAMBARA;
}