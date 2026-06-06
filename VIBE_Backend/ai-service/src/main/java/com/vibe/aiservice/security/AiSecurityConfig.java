package com.vibe.aiservice.security;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * THIS FILE IS INTENTIONALLY EMPTY / INERT.
 *
 * The canonical Security configuration for the AI service now lives at:
 *
 *   com.vibe.aiservice.config.AiSecurityConfig
 *
 * Having TWO classes annotated with @Configuration + @EnableWebSecurity in the
 * same Spring Boot application causes a ConflictingBeanDefinitionException at
 * startup because both try to register a SecurityFilterChain bean.
 *
 * Resolution: all security logic was consolidated into the config-package class.
 * This file is kept as a placeholder so no IDE/VCS tooling complains about a
 * missing file reference, but it registers NO Spring beans.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class AiSecurityConfig {
    // intentionally blank — see com.vibe.aiservice.config.AiSecurityConfig
}