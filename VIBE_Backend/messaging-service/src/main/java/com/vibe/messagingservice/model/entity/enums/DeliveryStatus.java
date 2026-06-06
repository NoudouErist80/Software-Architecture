package com.vibe.messagingservice.model.entity.enums;

/**
 * Message delivery status — mirrors WhatsApp/Telegram tick system.
 *
 * <ul>
 *   <li>SENT      — persisted in DB; one grey tick on the sender's UI</li>
 *   <li>DELIVERED — recipient(s) have connected and ACKed receipt; two grey ticks</li>
 *   <li>READ      — recipient(s) opened the conversation; two blue ticks (VIBE: blue/purple)</li>
 *   <li>FAILED    — could not persist (rare; surface to sender UI for retry)</li>
 * </ul>
 */
public enum DeliveryStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED
}
