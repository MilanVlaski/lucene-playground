package com.example.lucenedemo.ports;

import com.example.lucenedemo.domain.SearchResult;
import java.util.List;

/**
 * Driving port (API) for search operations.
 * Used by CLI, web, or tests to invoke core functionality.
 */
public interface SearchService extends AutoCloseable {

    /**
     * Perform a search using the database backend
     */
    SearchResult searchDatabase(String query);

    /**
     * Perform a search using the Lucene backend
     */
    SearchResult searchLucene(String query);

    /**
     * Generate test data for performance comparison
     * @param count number of emails to generate
     */
    void generateTestData(int count);

    /**
     * Get total number of emails in the system
     */
    long getStats();

    @Override
    void close();
}