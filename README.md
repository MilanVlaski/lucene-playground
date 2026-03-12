# Lucene Demo - Phase 1: Speed Comparison

## Overview
Phase 1 demonstrates the fundamental speed advantage of Apache Lucene's inverted index compared to traditional database full-text search.

## Architecture
The project follows hexagonal architecture with clear separation of concerns:

### Core Domain
- `Email` - Immutable record representing an email
- `SearchResult` - Search results with timing information

### Ports (Interfaces)
- `EmailRepository` - Driven port (SPI) for email storage
- `SearchService` - Driving port (API) for search operations

### Adapters (Implementations)
- `InMemoryEmailRepository` - Simple in-memory storage
- `SqliteEmailRepository` - SQLite with generated search column
- `LuceneEmailRepository` - Lucene with proper text analysis

### Core Service
- `SearchServiceImpl` - Orchestrates search operations

### CLI
- `Main` - Command-line interface

## Features Implemented

### Phase 1 Requirements
- ✅ Generate synthetic email dataset (100k+ emails)
- ✅ Implement `search-db <query>` command
- ✅ Implement `search-lucene <query>` command
- ✅ Compare performance with `compare <query>` command
- ✅ Demonstrate speed difference with `demo` command
- ✅ Show inverted index advantage

### Technical Highlights
- **Thread-safe implementations** with synchronized methods
- **Proper resource management** with AutoCloseable interfaces
- **Warm-up phase** for fair performance measurements
- **Multiple iterations** for stable timing results
- **Hexagonal architecture** - core has zero framework dependencies
- **In-memory storage** for demo simplicity (SQLite in-memory, Lucene ByteBuffersDirectory)

## Commands

### Basic Usage
```bash
# Generate test data
./run.sh generate 100000

# Search using database
./run.sh search-db john

# Search using Lucene
./run.sh search-lucene john

# Compare performance
./run.sh compare important

# Run complete demo
./run.sh demo

# Show statistics
./run.sh stats
```

### Expected Output Example
```
=== Performance Comparison ===

Warming up...
=== Results (averaged over 3 runs) ===
Database: 480 ms, 42 matches
Lucene:   12 ms, 42 matches
Speedup:  40.0x faster

✓ Lucene demonstrates significant speed advantage (10x+)
```

## Technical Details

### Database Search (SQLite)
- Uses `LIKE '%term%'` pattern matching on generated search column
- Creates index on search column for better performance
- Still requires full or partial index scans

### Lucene Search
- Uses `StandardAnalyzer` for text tokenization
- Creates inverted index during document indexing
- Search uses pre-built index (O(log n) vs O(n))
- Proper query parsing with term analysis

### Performance Characteristics
- **Small datasets (< 10k)**: Modest difference (1-2x) - both fit in memory
- **Medium datasets (100k)**: Noticeable difference (1.5-3x) with in-memory search
- **Large datasets (1M+)**: Dramatic difference (10-100x+) especially with disk I/O
- **Complex queries**: Lucene advantage increases with query complexity
- **Note**: Demo uses in-memory storage; real-world disk-based differences are larger

## Project Structure
```
src/main/java/com/example/lucenedemo/
├── domain/           # Core domain objects
├── ports/           # Interfaces (API/SPI)
├── adapters/        # Implementations
├── core/           # Business logic
└── cli/            # Command-line interface
```

## Building and Running

### Prerequisites
- Java 17+
- Maven 3.6+

### Build
```bash
mvn clean compile
```

### Run
```bash
./run.sh demo
```

### Direct Execution
```bash
mvn exec:java -Dexec.args="demo"
```

## Next Steps (Phase 2+)
1. **Phase 2**: Basic Search UX (ranking, relevance scoring)
2. **Phase 3**: Near-real-time indexing fundamentals
3. **Phase 4**: Immediate visibility with NRT
4. **Phase 5**: Concurrent reads during writes
5. **Phase 6**: Task search domain
6. **Phase 7**: Safe index rebuild for deployments
7. **Phase 8**: Complete redeployment simulation

## Design Principles
1. **Hexagonal Architecture**: Core independent of frameworks
2. **Immutable Objects**: Records with validation
3. **Resource Safety**: AutoCloseable implementations
4. **Thread Safety**: Synchronized critical sections
5. **Educational Focus**: Clear code demonstrating concepts

## Performance Tips
1. Generate at least 100k emails for meaningful comparison
2. Use the `demo` command for complete demonstration
3. Run multiple times to account for JVM warm-up
4. Try different query types (single words, phrases, partial matches)