package com.example.lucenedemo.ports;

import com.example.lucenedemo.domain.Email;
import java.util.List;

/**
 * Driven port (SPI) for email storage and retrieval.
 * Implemented by adapters (Lucene, SQLite, etc.)
 */
public interface EmailRepository extends AutoCloseable {

    /**
     * Save an email to the repository
     */
    void save(Email email);

    /**
     * Save multiple emails in batch
     */
    void saveAll(List<Email> emails);

    /**
     * Search for emails containing the given text
     * @param queryText text to search for
     * @return list of matching emails
     */
    List<Email> search(String queryText);

    /**
     * Get the number of emails in the repository
     */
    long count();

    /**
     * Clear all emails from the repository
     */
    void clear();

    /**
     * Close any resources used by the repository
     */
    void close();
}