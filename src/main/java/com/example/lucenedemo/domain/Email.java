package com.example.lucenedemo.domain;

import java.time.Instant;

public record Email(
    String id,
    String from,
    String to,
    String subject,
    String body,
    Instant timestamp
) {
    public Email {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Email ID cannot be null or blank");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("Email from cannot be null or blank");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Email to cannot be null or blank");
        }
        // subject and body can be null/empty
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}