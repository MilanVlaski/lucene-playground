package com.example.lucenedemo.adapters;

import com.example.lucenedemo.domain.Email;
import com.example.lucenedemo.ports.EmailRepository;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of EmailRepository.
 * Maps SQLite rows to Email domain objects.
 */
public class SqliteEmailRepository implements EmailRepository {

    private final Connection connection;

    public SqliteEmailRepository() {
        try {
            // Use in-memory database for demo
            this.connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            createTableIfNeeded();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite repository", e);
        }
    }

    public SqliteEmailRepository(String databasePath) {
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            createTableIfNeeded();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite repository at path: " + databasePath, e);
        }
    }

    private void createTableIfNeeded() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS emails (
                id TEXT PRIMARY KEY,
                from_email TEXT NOT NULL,
                to_email TEXT NOT NULL,
                subject TEXT,
                body TEXT,
                timestamp INTEGER NOT NULL,
                -- Create indexes for search performance
                search_text TEXT GENERATED ALWAYS AS (
                    lower(from_email) || ' ' ||
                    lower(to_email) || ' ' ||
                    coalesce(lower(subject), '') || ' ' ||
                    coalesce(lower(body), '')
                ) STORED
            )
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);

            // Create index on generated search column
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_emails_search ON emails(search_text)");
        }
    }

    @Override
    public synchronized void save(Email email) {
        String sql = """
            INSERT OR REPLACE INTO emails (id, from_email, to_email, subject, body, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, email.id());
            pstmt.setString(2, email.from());
            pstmt.setString(3, email.to());
            pstmt.setString(4, email.subject());
            pstmt.setString(5, email.body());
            pstmt.setLong(6, email.timestamp().toEpochMilli());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save email: " + email.id(), e);
        }
    }

    @Override
    public synchronized void saveAll(List<Email> emails) {
        String sql = """
            INSERT OR REPLACE INTO emails (id, from_email, to_email, subject, body, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                for (Email email : emails) {
                    pstmt.setString(1, email.id());
                    pstmt.setString(2, email.from());
                    pstmt.setString(3, email.to());
                    pstmt.setString(4, email.subject());
                    pstmt.setString(5, email.body());
                    pstmt.setLong(6, email.timestamp().toEpochMilli());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save emails in batch", e);
        }
    }

    @Override
    public synchronized List<Email> search(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        String searchTerm = "%" + queryText.toLowerCase() + "%";
        String sql = """
            SELECT id, from_email, to_email, subject, body, timestamp
            FROM emails
            WHERE search_text LIKE ?
            ORDER BY timestamp DESC
            LIMIT 1000
            """;

        List<Email> results = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, searchTerm);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Email email = mapRowToEmail(rs);
                    results.add(email);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search emails for query: " + queryText, e);
        }

        return results;
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM emails";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count emails", e);
        }
    }

    @Override
    public void clear() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM emails");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear emails", e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close SQLite connection", e);
        }
    }

    private Email mapRowToEmail(ResultSet rs) throws SQLException {
        return new Email(
            rs.getString("id"),
            rs.getString("from_email"),
            rs.getString("to_email"),
            rs.getString("subject"),
            rs.getString("body"),
            Instant.ofEpochMilli(rs.getLong("timestamp"))
        );
    }
}
