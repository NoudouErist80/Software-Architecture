package com.vibe.common.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * All 11 supported languages on VIBE — African-first platform.
 * AI translation powered by Claude Sonnet covers all these languages.
 */
@Getter
@RequiredArgsConstructor
public enum Language {
    ENGLISH("en",       "English"),
    FRENCH("fr",        "Français"),
    HAUSA("ha",         "Hausa"),
    EWONDO("ewo",       "Ewondo"),
    PIDGIN_ENGLISH("pcm","Pidgin English"),
    YORUBA("yo",        "Yorùbá"),
    TWI("tw",           "Twi (Ghana)"),
    FANTE("fat",        "Fante (Ghana)"),
    GA("gaa",           "Ga (Ghana)"),
    LINGALA("ln",       "Lingála"),
    BAMBARA("bm",       "Bamanankan");

    private final String code;
    private final String displayName;

    public static Language fromCode(String code) {
        if (code == null) return FRENCH;
        for (Language lang : values()) {
            if (lang.code.equalsIgnoreCase(code)) return lang;
        }
        return FRENCH;
    }
}
