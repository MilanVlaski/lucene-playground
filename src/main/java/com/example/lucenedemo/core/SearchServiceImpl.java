package com.example.lucenedemo.core;

import com.example.lucenedemo.domain.Email;
import com.example.lucenedemo.domain.SearchResult;
import com.example.lucenedemo.ports.EmailRepository;
import com.example.lucenedemo.ports.SearchService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core implementation of SearchService.
 * Follows hexagonal architecture: depends on ports, not frameworks.
 */
public class SearchServiceImpl implements SearchService {

    private final EmailRepository databaseRepository;
    private final EmailRepository luceneRepository;

    public SearchServiceImpl(EmailRepository databaseRepository, EmailRepository luceneRepository) {
        this.databaseRepository = databaseRepository;
        this.luceneRepository = luceneRepository;
    }

    @Override
    public SearchResult searchDatabase(String query) {
        Instant start = Instant.now();
        List<Email> results = databaseRepository.search(query);
        Duration elapsed = Duration.between(start, Instant.now());

        return new SearchResult(query, results, elapsed, "Database");
    }

    @Override
    public SearchResult searchLucene(String query) {
        Instant start = Instant.now();
        List<Email> results = luceneRepository.search(query);
        Duration elapsed = Duration.between(start, Instant.now());

        return new SearchResult(query, results, elapsed, "Lucene");
    }

    @Override
    public void generateTestData(int count) {
        System.out.printf("Generating %d test emails...\n", count);

        List<Email> emails = generateEmails(count);

        System.out.println("Saving to database...");
        databaseRepository.saveAll(emails);

        System.out.println("Saving to Lucene index...");
        luceneRepository.saveAll(emails);

        System.out.printf("Generated %d emails in both database and Lucene index\n", count);
    }

    @Override
    public long getStats() {
        return databaseRepository.count();
    }

    @Override
    public void close() {
        databaseRepository.close();
        luceneRepository.close();
    }

    private List<Email> generateEmails(int count) {
        List<Email> emails = new java.util.ArrayList<>(count);

        String[] domains = {"example.com", "company.com", "test.org", "demo.net", "mail.io"};
        String[] firstNames = {"john", "jane", "alice", "bob", "charlie", "diana", "eve", "frank"};
        String[] lastNames = {"smith", "johnson", "williams", "brown", "jones", "garcia", "miller", "davis"};
        String[] subjects = {"Meeting", "Project Update", "Important", "Urgent", "Follow up", "Review", "Question", "Proposal"};
        String[] bodies = {
            "Please review the attached document.",
            "Let's schedule a meeting for next week.",
            "I have some questions about the project.",
            "The deadline has been moved to Friday.",
            "Can you provide feedback on this?",
            "Here's the update you requested.",
            "Need your approval on this matter.",
            "Following up on our conversation."
        };

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            String fromFirstName = firstNames[random.nextInt(firstNames.length)];
            String fromLastName = lastNames[random.nextInt(lastNames.length)];
            String toFirstName = firstNames[random.nextInt(firstNames.length)];
            String toLastName = lastNames[random.nextInt(lastNames.length)];

            String from = String.format("%s.%s@%s", fromFirstName, fromLastName, domains[random.nextInt(domains.length)]);
            String to = String.format("%s.%s@%s", toFirstName, toLastName, domains[random.nextInt(domains.length)]);

            String subject = subjects[random.nextInt(subjects.length)] + " #" + (i + 1);
            String body = bodies[random.nextInt(bodies.length)];

            // Occasionally add some special terms for searching
            if (random.nextInt(10) == 0) {
                subject += " important";
                body += " This is very important!";
            }

            Email email = new Email(
                UUID.randomUUID().toString(),
                from,
                to,
                subject,
                body,
                Instant.now().minusSeconds(random.nextLong(86400 * 365)) // Random time in past year
            );

            emails.add(email);
        }

        return emails;
    }
}