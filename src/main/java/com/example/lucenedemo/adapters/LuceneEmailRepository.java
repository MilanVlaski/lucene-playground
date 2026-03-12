package com.example.lucenedemo.adapters;

import com.example.lucenedemo.domain.Email;
import com.example.lucenedemo.ports.EmailRepository;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
            // Create a boolean query to search across multiple fields
            BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();

            String lowerQuery = queryText.toLowerCase();

            // 1. Search in the combined search_text field using QueryParser
            // This uses the analyzer and should find tokenized terms
            org.apache.lucene.queryparser.classic.QueryParser queryParser =
                new org.apache.lucene.queryparser.classic.QueryParser("search_text", analyzer);
            queryParser.setDefaultOperator(org.apache.lucene.queryparser.classic.QueryParser.Operator.OR);

            try {
                Query parsedQuery = queryParser.parse(queryText);
                booleanQuery.add(parsedQuery, BooleanClause.Occur.SHOULD);
            } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                // If parsing fails, try a simple term query
                String analyzedTerm = analyzer.normalize("search_text", queryText).utf8ToString();
                if (!analyzedTerm.isBlank()) {
                    TermQuery fallbackQuery = new TermQuery(new Term("search_text", analyzedTerm));
                    booleanQuery.add(fallbackQuery, BooleanClause.Occur.SHOULD);
                }
            }

            // 2. Also try a phrase query for multi-word queries
            if (queryText.contains(" ")) {
                PhraseQuery phraseQuery = new PhraseQuery.Builder()
                    .add(new Term("search_text", queryText.toLowerCase()))
                    .build();
                booleanQuery.add(phraseQuery, BooleanClause.Occur.SHOULD);
            }

            // 3. Search in from and to fields (exact match on lowercase versions)
            // This ensures we match email addresses like "john.smith@example.com"
            TermQuery fromQuery = new TermQuery(new Term("from_lower", lowerQuery));
            booleanQuery.add(fromQuery, BooleanClause.Occur.SHOULD);

            TermQuery toQuery = new TermQuery(new Term("to_lower", lowerQuery));
            booleanQuery.add(toQuery, BooleanClause.Occur.SHOULD);

            // 4. Also try wildcard queries for partial matches in email addresses
            // e.g., "john" should match "john.smith@example.com"
            WildcardQuery fromWildcard = new WildcardQuery(new Term("from_lower", "*" + lowerQuery + "*"));
            booleanQuery.add(fromWildcard, BooleanClause.Occur.SHOULD);

            WildcardQuery toWildcard = new WildcardQuery(new Term("to_lower", "*" + lowerQuery + "*"));
            booleanQuery.add(toWildcard, BooleanClause.Occur.SHOULD);

            // 5. Search in subject and body fields too
            WildcardQuery subjectWildcard = new WildcardQuery(new Term("subject", "*" + lowerQuery + "*"));
            booleanQuery.add(subjectWildcard, BooleanClause.Occur.SHOULD);

            WildcardQuery bodyWildcard = new WildcardQuery(new Term("body", "*" + lowerQuery + "*"));
            booleanQuery.add(bodyWildcard, BooleanClause.Occur.SHOULD);

            TopDocs topDocs = searcher.search(booleanQuery.build(), 1000);
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