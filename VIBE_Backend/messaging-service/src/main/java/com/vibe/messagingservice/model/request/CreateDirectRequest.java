package com.vibe.messagingservice.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for creating (or retrieving) a direct 1-to-1 conversation.
 *
 * <p>{@code participantUsername} and {@code participantPhone} are optional but
 * recommended — when provided they are stored on the Conversation entity so
 * the client can display the other user without a round-trip to auth-service
 * (WhatsApp Web / Messenger pattern).
 *
 * <p>{@code participantPhone} enables the WhatsApp "unknown contact" UX:
 * if a user receives a message from someone not in their contacts, they see
 * the phone number instead of a username.
 */
@Data
public class CreateDirectRequest {

    /** UUID of the other participant (required). */
    @NotBlank
    private String participantId;

    /** @username of the other participant (optional but recommended). */
    private String participantUsername;

    /**
     * E.164 phone number of the other participant (optional).
     * e.g. "+237612345678"
     */
    private String participantPhone;
}
