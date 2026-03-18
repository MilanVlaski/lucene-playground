# Lucene Playground

A minimalist Java application demonstrating Apache Lucene's performance advantages over traditional database full-text search (SQLite with LIKE queries).

## Purpose

This project showcases three key Lucene production properties:
- **Speed**: Inverted index enables fast full-text search
- **Near real-time indexing**: Changes become searchable almost immediately
- **Safe deployment**: Proper resource management and clean architecture

## Build & Run

**Prerequisites:**
- Java 17+
- Maven 3.6+

**Run the console app:**
```shell
  ./run.sh
```

## Implementation Notes

- **Search strategy**: Lucene uses a three-pronged approach:
  1. QueryParser on combined `search_text` field for token-based matching
  2. Wildcard queries on `from_lower`/`to_lower` for partial email address matching
  3. Wildcard query on `search_text` for substring matching within tokens (e.g., "mat" → "matter")
- **Input safety**: All wildcard queries escape special characters (`*`, `?`, `[`, `]`, `\`) to prevent parsing errors
- **Data storage**: In-memory SQLite and Lucene's `ByteBuffersDirectory` for demo simplicity
- **Performance measurement**: Warm-up phase + average over 3 iterations
- **Resource cleanup**: AutoCloseable pattern ensures proper cleanup of database connections and Lucene resources

## Future Phases

See [docs/Roadmap.md](./docs/Roadmap.md) for planned enhancements:
- Phase 2: Shadow indexing for zero-downtime deployments
- Phase 3: Multi-index management and rollover
