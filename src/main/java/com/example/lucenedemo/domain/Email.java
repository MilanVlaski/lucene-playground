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

    public boolean containsText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String searchText = text.toLowerCase();
        return (subject != null && subject.toLowerCase().contains(searchText)) ||
               (body != null && body.toLowerCase().contains(searchText)) ||
               from.toLowerCase().contains(searchText) ||
               to.toLowerCase().contains(searchText);
    }
}