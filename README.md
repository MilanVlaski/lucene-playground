# Lucene Speed Demo

A minimalist Java application demonstrating Apache Lucene's performance advantages over traditional database full-text search (SQLite with LIKE queries).

## Purpose

This project showcases three key Lucene production properties:
- **Speed**: Inverted index enables fast full-text search
- **Near real-time indexing**: Changes become searchable almost immediately
- **Safe deployment**: Proper resource management and clean architecture

## Phase 1 Features

The complete demo includes these commands:

| Command | Description |
|---------|-------------|
| `generate <count>` | Generate test emails (e.g., `generate 100000`) |
| `search-db <query>` | Search using SQLite (LIKE %query%) |
| `search-lucene <query>` | Search using Lucene (QueryParser + wildcards) |
| `compare <query>` | Compare search performance side-by-side |
| `stats` | Show total email count |
| `demo` | Run complete demo with multiple queries |

**Example:** `./run.sh demo` generates 100k emails and runs 4 comparison searches.

## Architecture

This project follows **hexagonal (ports & adapters) architecture**:

```
cli/Main
    ↓ (uses)
ports/SearchService (API)
    ↓ (orchestrates)
core/SearchServiceImpl
    ↓ (delegates to)
adapters/ (LuceneEmailRepository, SqliteEmailRepository)
    ↓ (maps to)
domain/ (Email, SearchResult)
```

The core business logic has zero framework dependencies and can be tested independently.

## Build & Run

**Prerequisites:**
- Java 17+
- Maven 3.6+

**Build:**
```bash
mvn clean package
```

**Run demo:**
```bash
./run.sh demo
```

**Generate data:**
```bash
./run.sh generate 100000
```

**Compare searches:**
```bash
./run.sh compare john
```

## Implementation Notes

- **Search strategy**: Lucene uses QueryParser on a combined `search_text` field plus wildcard queries for partial email address matching
- **Data storage**: In-memory SQLite and Lucene's `ByteBuffersDirectory` for demo simplicity
- **Performance measurement**: Warm-up phase + average over 3 iterations
- **Resource cleanup**: AutoCloseable pattern ensures proper cleanup of database connections and Lucene resources

## Future Phases

See [docs/Roadmap.md](./docs/Roadmap.md) for planned enhancements:
- Phase 2: Shadow indexing for zero-downtime deployments
- Phase 3: Multi-index management and rollover
