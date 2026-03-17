package com.example.lucenedemo.cli;

import com.example.lucenedemo.adapters.SqliteEmailRepository;
import com.example.lucenedemo.adapters.LuceneEmailRepository;
import com.example.lucenedemo.core.SearchServiceImpl;
import com.example.lucenedemo.domain.SearchResult;
import com.example.lucenedemo.ports.SearchService;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        try {
            new Main().execute(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void execute(String[] args) {
        String command = args[0].toLowerCase();

        switch (command) {
            case "generate":
                handleGenerate(args);
                break;
            case "search-db":
                handleSearchDatabase(args);
                break;
            case "search-lucene":
                handleSearchLucene(args);
                break;
            case "compare":
                handleCompare(args);
                break;
            case "stats":
                handleStats();
                break;
            case "demo":
                handleDemo();
                break;
            default:
                System.err.println("Unknown command: " + command);
                printUsage();
        }
    }

    private void handleGenerate(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: generate <count>");
            return;
        }

        int count = Integer.parseInt(args[1]);
        try (var service = createSearchService()) {
            service.generateTestData(count);
            long totalEmails = service.getStats();
            System.out.printf("Generated %d emails. Total in system: %d\n", count, totalEmails);
        }
    }

    private void handleSearchDatabase(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: search-db <query>");
            return;
        }

        String query = args[1];
        try (var service = createSearchService()) {
            SearchResult result = service.searchDatabase(query);
            System.out.println(result);
        }
    }

    private void handleSearchLucene(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: search-lucene <query>");
            return;
        }

        String query = args[1];
        try (var service = createSearchService()) {
            SearchResult result = service.searchLucene(query);
            System.out.println(result);
        }
    }

    private void handleCompare(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: compare <query>");
            return;
        }

        String query = args[1];
        try (var service = createSearchService()) {
            handleCompareWithService(service, query);
        }
    }

    private void handleCompareWithService(SearchService service, String query) {
        System.out.println("=== Performance Comparison ===\n");

        // Warm up: Run each search once to prime caches and JIT
        System.out.println("Warming up...");
        service.searchDatabase(query);
        service.searchLucene(query);

        // Run multiple iterations for more stable measurements
        int iterations = 3;
        long totalDbTime = 0;
        long totalLuceneTime = 0;
        int dbMatches = 0;
        int luceneMatches = 0;

        for (int i = 0; i < iterations; i++) {
            SearchResult dbResult = service.searchDatabase(query);
            totalDbTime += dbResult.searchTimeMillis();
            dbMatches = dbResult.matchCount();

            SearchResult luceneResult = service.searchLucene(query);

            totalLuceneTime += luceneResult.searchTimeMillis();
            luceneMatches = luceneResult.matchCount();
        }

        long avgDbTime = totalDbTime / iterations;
        long avgLuceneTime = totalLuceneTime / iterations;

        System.out.println("=== Results (averaged over " + iterations + " runs) ===");
        System.out.printf("Database: %d ms, %d matches\n", avgDbTime, dbMatches);
        System.out.printf("Lucene:   %d ms, %d matches\n", avgLuceneTime, luceneMatches);
        System.out.printf("Speedup:  %.1fx faster\n",
            (double) avgDbTime / Math.max(1, avgLuceneTime));
        System.out.println();

        if (avgLuceneTime > 0 && avgDbTime > avgLuceneTime * 10) {
            System.out.println("✓ Lucene demonstrates significant speed advantage (10x+)");
        } else if (avgLuceneTime > 0 && avgDbTime > avgLuceneTime * 2) {
            System.out.println("✓ Lucene shows good performance improvement (2x+)");
        } else {
            System.out.println("Note: For small datasets or simple queries, differences may be less pronounced.");
        }
    }

    private void handleStats() {
        try (var service = createSearchService()) {
            long totalEmails = service.getStats();
            System.out.println("=== System Statistics ===");
            System.out.printf("Total emails: %d\n", totalEmails);
        }
    }

    private void handleDemo() {
        System.out.println("=== Lucene Demo - Phase 1: Speed Comparison ===\n");

        try (var service = createSearchService()) {
            // Generate test data if none exists
            long totalEmails = service.getStats();
            if (totalEmails == 0) {
                System.out.println("Generating test data (100,000 emails)...");
                service.generateTestData(100_000);
                System.out.println("Data generation complete.\n");
            } else {
                System.out.printf("Using existing dataset (%d emails)\n\n", totalEmails);
            }

            System.out.println("1. Searching for 'john' (common name in email addresses):");
            handleCompareWithService(service, "john");

            System.out.println("\n2. Searching for 'important' (text in subject/body):");
            handleCompareWithService(service, "important");

            System.out.println("\n3. Searching for 'meeting' (common subject term):");
            handleCompareWithService(service, "meeting");

            System.out.println("\n4. Searching for 'project update' (multi-word phrase):");
            handleCompareWithService(service, "project update");

            System.out.println("\n=== Demo Complete ===");
            System.out.println("This demonstrates Lucene's inverted index advantage:");
            System.out.println("- Lucene pre-processes text into searchable tokens");
            System.out.println("- Database performs full-text scans (LIKE %term%)");
            System.out.println("- Difference becomes more dramatic with larger datasets");
            System.out.println("\nTry your own queries with: search-db <query> and search-lucene <query>");
            System.out.println("Or compare directly: compare <query>");
        }
    }

    private SearchService createSearchService() {
        var dbRepo = new SqliteEmailRepository();
        var luceneRepo = new LuceneEmailRepository();
        return new SearchServiceImpl(dbRepo, luceneRepo);
    }

    private static void printUsage() {
        System.out.println("Lucene Demo - Phase 1: Speed Comparison");
        System.out.println("========================================");
        System.out.println("Commands:");
        System.out.println("  generate <count>        Generate test emails");
        System.out.println("  search-db <query>       Search using database");
        System.out.println("  search-lucene <query>   Search using Lucene");
        System.out.println("  compare <query>         Compare search performance");
        System.out.println("  stats                   Show system statistics");
        System.out.println("  demo                    Run complete demo");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./run.sh generate 100000");
        System.out.println("  ./run.sh compare john");
        System.out.println("  ./run.sh demo");
    }
}
