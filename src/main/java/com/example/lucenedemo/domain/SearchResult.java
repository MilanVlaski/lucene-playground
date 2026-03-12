package com.example.lucenedemo.domain;

import java.time.Duration;
import java.util.List;

public record SearchResult(
    String query,
    List<Email> matches,
    Duration searchTime,
    String backend
) {
    public SearchResult {
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }
        if (matches == null) {
            matches = List.of();
        }
        if (searchTime == null) {
            searchTime = Duration.ZERO;
        }
        if (backend == null || backend.isBlank()) {
            throw new IllegalArgumentException("Backend cannot be null or blank");
        }
    }

    public int matchCount() {
        return matches.size();
    }

    public long searchTimeMillis() {
        return searchTime.toMillis();
    }

    @Override
    public String toString() {
        return String.format(
            "Query: '%s'\n%s search: %d ms\nMatches: %d\n%s",
            query,
            backend,
            searchTimeMillis(),
            matchCount(),
            matches.isEmpty() ? "No results found" : formatMatches()
        );
    }

    private String formatMatches() {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(matches.size(), 5);
        for (int i = 0; i < limit; i++) {
            Email email = matches.get(i);
            sb.append(String.format("%d. %s -> %s: %s\n",
                i + 1,
                email.from(),
                email.to(),
                truncate(email.subject(), 50)
            ));
        }
        if (matches.size() > 5) {
            sb.append(String.format("... and %d more\n", matches.size() - 5));
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text != null ? text : "";
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}