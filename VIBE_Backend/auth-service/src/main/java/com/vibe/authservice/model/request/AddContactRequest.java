package com.vibe.authservice.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for adding a contact.
 *
 * <p>{@code userId} is the UUID of the user being added (required).</p>
 *
 * <p>{@code displayName} is the custom name the owner wants to save the contact as
 * (optional — like WhatsApp where you save "Mama" instead of the full name).
 * If omitted, the user's own fullName is used as the display name.</p>
 */
@Data
public class AddContactRequest {

    /** UUID of the user to add as a contact (required). */
    @NotBlank
    private String userId;

    /**
     * Custom display name chosen by the owner.
     * e.g. "Mama", "Jean-Pierre", "Boss", "Daniel from work"
     * If null or blank, the contact's own fullName is used.
     */
    private String displayName;
}