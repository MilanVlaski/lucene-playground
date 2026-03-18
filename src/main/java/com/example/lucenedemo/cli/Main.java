package com.example.lucenedemo.cli;

import com.example.lucenedemo.adapters.LuceneEmailRepository;
import com.example.lucenedemo.adapters.SqliteEmailRepository;
import com.example.lucenedemo.core.SearchServiceImpl;
import com.example.lucenedemo.domain.SearchResult;
import com.example.lucenedemo.ports.SearchService;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        new Main().runInteractive();
    }

    private void runInteractive() {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║     Lucene Demo - Phase 1: Interactive Menu          ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();

        try (var dbRepo = new SqliteEmailRepository();
             var luceneRepo = new LuceneEmailRepository();
             var service = new SearchServiceImpl(dbRepo, luceneRepo);
             var scanner = new Scanner(System.in)) {

            boolean running = true;
            while (running) {
                printMenu();
                System.out.print("Choose an option (0-6): ");
                String input = scanner.nextLine().trim();

                try {
                    int choice = Integer.parseInt(input);
                    running = handleMenuChoice(choice, scanner, service);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a number 0-6.");
                }
            }

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void printMenu() {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║                    Main Menu                          ║");
        System.out.println("╠════════════════════════════════════════════════════════╣");
        System.out.println("║  1. Generate test emails                              ║");
        System.out.println("║  2. Search in database (SQLite)                       ║");
        System.out.println("║  3. Search in Lucene                                 ║");
        System.out.println("║  4. Compare search performance                       ║");
        System.out.println("║  5. Show statistics                                  ║");
        System.out.println("║  6. Run full demo                                   ║");
        System.out.println("║  0. Exit                                            ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }

    private boolean handleMenuChoice(int choice, Scanner scanner, SearchService service) {
        if (choice == 0) {
            System.out.println("Exiting...");
            return false;
        } else if (choice == 1) {
            return handleGenerate(scanner, service);
        } else if (choice == 2) {
            return handleSearchDatabase(scanner, service);
        } else if (choice == 3) {
            return handleSearchLucene(scanner, service);
        } else if (choice == 4) {
            return handleCompare(scanner, service);
        } else if (choice == 5) {
            handleStats(service);
            return true;
        } else if (choice == 6) {
            return handleDemo(service);
        } else {
            System.out.println("Invalid choice. Please select 0-6.");
            return true;
        }
    }

    private boolean handleGenerate(Scanner scanner, SearchService service) {
        System.out.print("Enter number of emails to generate (e.g., 10000): ");
        String countInput = scanner.nextLine().trim();
        try {
            int count = Integer.parseInt(countInput);
            System.out.println();
            System.out.println("Generating " + count + " test emails...");
            service.generateTestData(count);
            long totalEmails = service.getStats();
            System.out.printf("✓ Generated %d emails. Total in system: %d\n", count, totalEmails);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format.");
        }
        return true;
    }

    private boolean handleSearchDatabase(Scanner scanner, SearchService service) {
        System.out.print("Enter search query: ");
        String query = scanner.nextLine().trim();
        if (query.isBlank()) {
            System.out.println("Query cannot be empty.");
        } else {
            System.out.println();
            SearchResult result = service.searchDatabase(query);
            System.out.println(result);
        }
        System.out.println("Press Enter to continue.");
        return scanner.nextLine().isEmpty();
    }

    private boolean handleSearchLucene(Scanner scanner, SearchService service) {
        System.out.print("Enter search query: ");
        String query = scanner.nextLine().trim();
        if (query.isBlank()) {
            System.out.println("Query cannot be empty.");
        } else {
            System.out.println();
            SearchResult result = service.searchLucene(query);
            System.out.println(result);
        }
        System.out.println("Press Enter to continue.");
        return scanner.nextLine().isEmpty();
    }

    private boolean handleCompare(Scanner scanner, SearchService service) {
        System.out.print("Enter query to compare: ");
        String query = scanner.nextLine().trim();
        if (query.isBlank()) {
            System.out.println("Query cannot be empty.");
        } else {
            System.out.println();
            handleCompareWithService(service, query);
        }
        return true;
    }

    private void handleCompareWithService(SearchService service, String query) {
        System.out.println("=== Performance Comparison ===\n");

        // Warm up
        System.out.println("Warming up...");
        service.searchDatabase(query);
        service.searchLucene(query);

        // Run multiple iterations
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

    private boolean handleStats(SearchService service) {
        long totalEmails = service.getStats();
        System.out.println("=== System Statistics ===");
        System.out.printf("Total emails: %d\n", totalEmails);
        return true;
    }

    private boolean handleDemo(SearchService service) {
        System.out.println("=== Lucene Demo - Phase 1: Speed Comparison ===\n");

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
        System.out.println("\nYou can now explore other commands from the menu!");
        return true;
    }
}
