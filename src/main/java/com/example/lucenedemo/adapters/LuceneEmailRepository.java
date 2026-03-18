package com.example.lucenedemo.adapters;

import com.example.lucenedemo.domain.Email;
import com.example.lucenedemo.ports.EmailRepository;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.apache.lucene.queryparser.classic.QueryParser.*;

/**
 * Lucene implementation of EmailRepository.
 * Maps Email domain objects to Lucene Documents and back.
 */
public class LuceneEmailRepository implements EmailRepository {

    private final Directory directory;
    private final Analyzer analyzer;
    private IndexWriter writer;
    private IndexSearcher searcher;
    private DirectoryReader reader;

    public LuceneEmailRepository() {
        try {
            this.directory = new ByteBuffersDirectory(); // In-memory for demo
            this.analyzer = new StandardAnalyzer();
            initializeWriter();
            refreshSearcher();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Lucene repository", e);
        }
    }

    public LuceneEmailRepository(Directory directory) {
        try {
            this.directory = directory;
            this.analyzer = new StandardAnalyzer();
            initializeWriter();
            refreshSearcher();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Lucene repository with custom directory", e);
        }
    }

    private void initializeWriter() throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(directory, config);
    }

    private synchronized void refreshSearcher() throws IOException {
        DirectoryReader newReader;
        if (reader == null) {
            // First time: open fresh reader
            newReader = DirectoryReader.open(writer);
        } else {
            // Subsequent: try to open changed reader
            newReader = DirectoryReader.openIfChanged(reader, writer);
        }

        if (newReader != null) {
            if (reader != null) {
                reader.close();
            }
            this.reader = newReader;
            this.searcher = new IndexSearcher(newReader);
        }
        // If newReader is null, reader is already up-to-date
    }

    @Override
    public void save(Email email) {
        try {
            Document doc = createDocument(email);
            writer.addDocument(doc);
            writer.commit();
            refreshSearcher();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save email: " + email.id(), e);
        }
    }

    @Override
    public void saveAll(List<Email> emails) {
        try {
            for (Email email : emails) {
                Document doc = createDocument(email);
                writer.addDocument(doc);
            }
            writer.commit();
            refreshSearcher();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save emails in batch", e);
        }
    }

    @Override
    public synchronized List<Email> search(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        try {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            String lowerQuery = queryText.toLowerCase();
            String escapedLowerQuery = QueryParser.escape(lowerQuery);

            // Strategy 1: QueryParser on combined search_text field
            // This covers most full-text search needs with proper tokenization
            var queryParser = new QueryParser("search_text", analyzer);
            queryParser.setDefaultOperator(Operator.OR);

            try {
                Query parsedQuery = queryParser.parse(queryText);
                builder.add(parsedQuery, BooleanClause.Occur.SHOULD);
            } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                // If parsing fails, skip this strategy and rely on wildcards
            }

            // Strategy 2: Wildcard queries for partial email address matching
            // This allows "john" to match "john.smith@example.com"
            builder.add(new WildcardQuery(new Term("from_lower", "*" + escapedLowerQuery + "*")), BooleanClause.Occur.SHOULD);
            builder.add(new WildcardQuery(new Term("to_lower", "*" + escapedLowerQuery + "*")), BooleanClause.Occur.SHOULD);

            // Strategy 3: Wildcard on combined search field for substring matching within tokens
            // Note: Leading wildcards are expensive; this is acceptable for demo purposes
            builder.add(new WildcardQuery(new Term("search_text", "*" + escapedLowerQuery + "*")), BooleanClause.Occur.SHOULD);

            TopDocs topDocs = searcher.search(builder.build(), 1000);
            return convertTopDocsToEmails(topDocs);

        } catch (IOException e) {
            throw new RuntimeException("Failed to search emails for query: " + queryText, e);
        }
    }

    @Override
    public long count() {
        try {
            return reader.numDocs();
        } catch (Exception e) {
            throw new RuntimeException("Failed to count emails in Lucene index", e);
        }
    }

    @Override
    public void clear() {
        try {
            writer.deleteAll();
            writer.commit();
            refreshSearcher();
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear Lucene index", e);
        }
    }

    @Override
    public void close() {
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (directory != null) {
                directory.close();
            }
            if (analyzer != null) {
                analyzer.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to close Lucene resources", e);
        }
    }

    private Document createDocument(Email email) {
        Document doc = new Document();

        // ID field (not analyzed, stored)
        doc.add(new StringField("id", email.id(), Field.Store.YES));

        // From field (analyzed, stored)
        doc.add(new TextField("from", email.from(), Field.Store.YES));
        // Lowercase version for exact matching
        doc.add(new StringField("from_lower", email.from().toLowerCase(), Field.Store.NO));

        // To field (analyzed, stored)
        doc.add(new TextField("to", email.to(), Field.Store.YES));
        // Lowercase version for exact matching
        doc.add(new StringField("to_lower", email.to().toLowerCase(), Field.Store.NO));

        // Subject field (analyzed, stored)
        if (email.subject() != null) {
            doc.add(new TextField("subject", email.subject(), Field.Store.YES));
        }

        // Body field (analyzed, stored)
        if (email.body() != null) {
            doc.add(new TextField("body", email.body(), Field.Store.YES));
        }

        // Timestamp field (not analyzed, stored as long)
        doc.add(new LongPoint("timestamp", email.timestamp().toEpochMilli()));
        doc.add(new StoredField("timestamp", email.timestamp().toEpochMilli()));

        // Combined search field for multi-field search
        // Include all searchable text in one field for simple queries
        StringBuilder searchText = new StringBuilder();
        searchText.append(email.from()).append(" ");
        searchText.append(email.to()).append(" ");
        if (email.subject() != null) {
            searchText.append(email.subject()).append(" ");
        }
        if (email.body() != null) {
            searchText.append(email.body()).append(" ");
        }

        doc.add(new TextField("search_text", searchText.toString(), Field.Store.NO));

        return doc;
    }

    private List<Email> convertTopDocsToEmails(TopDocs topDocs) throws IOException {
        List<Email> emails = new ArrayList<>();

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            emails.add(convertDocumentToEmail(doc));
        }

        return emails;
    }

    private Email convertDocumentToEmail(Document doc) {
        return new Email(
            doc.get("id"),
            doc.get("from"),
            doc.get("to"),
            doc.get("subject"),
            doc.get("body"),
            Instant.ofEpochMilli(Long.parseLong(doc.get("timestamp")))
        );
    }
}
