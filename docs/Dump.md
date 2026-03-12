## To do
```java
// Idk if the "Search" name makes sense, as this is the "entry point" for both updates, and searches, reading and writing
class LuceneIndex {

    search()
    update()
    updateAndRefresh()
    refresh()   // reopen searcher / reader
    update?() // TODO
}

class EmailIndex extends LuceneIndex {

}

class TaskIndex extends LuceneIndex {

}
```

For updateAndMakeVisible() use something like this:
```java
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory; // or FSDirectory
import org.apache.lucene.document.Document;

public class TransactionalUpdateExample {

    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;
    private final ControlledRealTimeReopenThread<IndexSearcher> nrtReopenThread;

    public TransactionalUpdateExample() throws Exception {
        Directory dir = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig();

        // 1. Create IndexWriter
        this.indexWriter = new IndexWriter(dir, config);

        // 2. Create SearcherManager (wraps the writer for NRT)
        // Passing 'true' for applyAllDeletes ensures deletions are also visible
        this.searcherManager = new SearcherManager(indexWriter, true, true, new SearcherFactory());

        // 3. Create ControlledRealTimeReopenThread
        // maxStaleSec (5.0): Max time a searcher can be stale if nobody is waiting
        // minStaleSec (0.025): Min time between refreshes when someone IS waiting
        this.nrtReopenThread = new ControlledRealTimeReopenThread<>(
            indexWriter,
            searcherManager,
            5.0,   // Target max stale (seconds)
            0.025  // Target min stale (seconds)
        );

        // Start the background thread
        this.nrtReopenThread.start();
    }

    public void updateAndMakeVisible(Document doc) throws InterruptedException, java.io.IOException {
        // A. Perform the update and capture the generation ID
        long generation = indexWriter.addDocument(doc);

        // B. Block until this specific generation is visible
        // This triggers a refresh if needed and waits
        nrtReopenThread.waitForGeneration(generation);

        // C. At this line, the update is guaranteed to be in the current searcher
    }

    public void search() throws java.io.IOException {
        // D. Acquire the searcher (it will be up-to-date)
        IndexSearcher searcher = searcherManager.acquire();
        try {
            // Execute search...
            System.out.println("Searcher matches: " + searcher.count(new org.apache.lucene.search.MatchAllDocsQuery()));
        } finally {
            searcherManager.release(searcher);
        }
    }

    public void close() throws java.io.IOException {
        nrtReopenThread.close();
        searcherManager.close();
        indexWriter.close();
    }
}

```

## Shortened To Do

## Full To Do
A good demo should show **three production properties**:

1. **Speed** (why Lucene exists)
2. **Near-real-time indexing** (updates become visible safely)
3. **Safe deployment / rebuilds** (no broken index during deploy)

Your roadmap already has these ideas, but it’s mixed. A clearer structure is **phases**, each proving one property.

---

# Lucene Demo Roadmap

## 1. Baseline: Search vs Database

**Goal:** prove Lucene search speed.
Steps:

1. Generate dataset
    - ~100k–1M fake emails
    - Store them in:
        - flat files **or**
        - SQLite

2. Implement two search paths

```
search-db <query>
search-lucene <query>
```

3. Measure time

Example output:

```
Query: "john"

DB search:      480 ms
Lucene search:   12 ms
```

4. Repeat several times to show stable latency.


What this demonstrates
- inverted index advantage
- scalable search

---

# 2. Basic Search UX

**Goal:** show realistic query behavior.

Command:
```
search-email john@example.com
```

Output:
```
Top 10 results
1. john@example.com
2. john.smith@example.com
3. johnny@example.com
...
```

Features to demonstrate:
- partial matching
- ranking
- top-N results

---

# 3. Near-Real-Time Indexing

Lucene’s real strength.

### Scenario A — invisible before refresh

```
insert-email newperson@example.com
search newperson
```

Expected:

```
No results
```

Because the reader hasn't refreshed yet.

---

### Scenario B — visible after refresh

```
refresh
search newperson
```

Expected:

```
1. newperson@example.com
```

This demonstrates:

**IndexWriter vs Searcher refresh behavior**

---

# 4. Immediate Visibility (NRT)

Now demonstrate **near-real-time search**.

Use:

```
DirectoryReader.open(IndexWriter)
```

or

```
SearcherManager
```

Scenario:

```
insert-email alice@corp.com
search alice
```

Expected:

```
1 result
```

without full commit.

Explain:

- writes go to RAM buffer

- new searcher sees them immediately


---

# 5. Concurrent Reads During Writes

Production requirement: **search never blocks indexing.**

Demo:

1. Start background search thread:


```
while true:
    search("john")
```

2. Meanwhile insert many docs:


```
bulk-insert 10000
```

Expected behavior:

- searches continue normally

- no latency spike


Console example:

```
Search latency: 8ms
Search latency: 9ms
Indexing 10k docs...
Search latency: 10ms
Search latency: 8ms
```

This proves:

**Lucene's segment architecture**

---

# 6. Task Search Example (Realistic Domain)

Instead of only emails, add **tasks**.

Example:

```
add-task "Fix login bug"
add-task "Update payment integration"
add-task "Write integration tests"
```

Search:

```
search-task login
```

Result:

```
1. Fix login bug
```

Purpose:

- shows text search usefulness

- multiple fields


---

# 7. Safe Index Rebuild (Deployment Scenario)

This is the **most important production feature**.

Goal: avoid searching a **half-built index**.

### Strategy

Use **shadow index directories**

```
index/
index-20260307-1015/
index-20260307-1030/
```

Steps:

1. App currently uses


```
index/current -> index-20260307-1015
```

2. Rebuild index into


```
index-20260307-1030
```

3. When finished:


```
ln -sfn index-20260307-1030 index/current
```

4. Reload searcher


```
luceneIndex.reload()
```

Now the switch is **instant**.

Search never sees a broken index.

---

# 8. Redeployment Simulation

Demo script:

```
1. App running
2. Build new index in background
3. Flip symlink
4. Reload searcher
```

Console:

```
Building shadow index...
Indexed 1,000,000 docs

Switching index...
Reloading searcher...

Search available immediately.
```

This demonstrates **blue-green index deployment**.

---

# Final Demo Flow (What a viewer sees)

1️⃣ Generate 1M emails
2️⃣ Show **DB search = slow**
3️⃣ Show **Lucene search = fast**

4️⃣ Insert doc → not visible
5️⃣ Refresh → visible

6️⃣ Insert doc → **NRT visible immediately**

7️⃣ Run **search thread + indexing thread**

8️⃣ Build **shadow index**

9️⃣ **Flip symlink**

🔟 Reload searcher safely

---

# Optional (very impressive additions)

If you want the demo to look **seriously production-grade**:

Add:

### Segment statistics

```
print-segments
```

### Index size

```
index-size
```

### Query explanation

```
explain "john"
```

### Highlighting results

```
Fix <b>login</b> bug
```

---

If you'd like, I can also show a **very clean class structure for the demo**, because your earlier `LuceneIndex` / `EmailSearch` design can be made much clearer for a production-style example.

**The Process:**
1. **Build:** Your app creates a new folder: `/data/index_2026_03_07_T1000`.
2. **Populate:** You write all your data into that specific timestamped folder.
3. **The Flip (Infra/OS):** You run a command (like `ln -nsf`) that tells the OS: "The shortcut `index_active` now points to `index_2026_03_07_T1000` instead of the old one."
4. **The Reload (Code):** Your Java code needs to call `DirectoryReader.openIfChanged()` or simply re-initialize the `IndexSearcher` to pick up the new files at that path.
- [ ] The "code" will be able to just reload that.
## Reload
### Reload 1 (verbose)
```java
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

public class LuceneIndexManager {

    private final Path rootPath;
    private final Path symlinkPath;

    private final AtomicReference<IndexSearcher> searcherRef = new AtomicReference<>();

    public LuceneIndexManager(Path rootPath, Path symlinkPath) throws IOException {
        this.rootPath = rootPath;
        this.symlinkPath = symlinkPath;
        refreshSearcher();
    }

    public IndexSearcher getSearcher() {
        return searcherRef.get();
    }

    public void rebuildFromScratch() throws IOException {
        // A. create unique directory
        Path newIndexPath = rootPath.resolve("idx_" + System.currentTimeMillis());

        // B. build shadow index
        populateIndex(newIndexPath);

        // C. atomic flip
        updateSymlink(newIndexPath);

        // D. reload searcher
        refreshSearcher();
    }

    private void populateIndex(Path indexPath) throws IOException {
        Files.createDirectories(indexPath);

        try (Directory dir = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {

            // TODO add documents here
            // writer.addDocument(...);

            writer.commit();
        }
    }

    private void updateSymlink(Path newTarget) throws IOException {
        Path tempLink = symlinkPath.resolveSibling(symlinkPath.getFileName() + "_tmp");

        Files.deleteIfExists(tempLink);
        Files.createSymbolicLink(tempLink, newTarget.getFileName());

        Files.move(
                tempLink,
                symlinkPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private void refreshSearcher() throws IOException {
        Directory dir = FSDirectory.open(symlinkPath);
        DirectoryReader newReader = DirectoryReader.open(dir);
        IndexSearcher newSearcher = new IndexSearcher(newReader);

        IndexSearcher oldSearcher = searcherRef.getAndSet(newSearcher);

        if (oldSearcher != null) {
            oldSearcher.getIndexReader().close();
        }

        dir.close();
    }

    public void close() throws IOException {
        IndexSearcher s = searcherRef.get();
        if (s != null) {
            s.getIndexReader().close();
        }
    }
}
```
## Two ways to make searches imediatelly visible
### ControlledRealTimeReopenThread
More "sophisticated" and controlled.
```java
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory; // or FSDirectory
import org.apache.lucene.document.Document;

public class TransactionalUpdateExample {

    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;
    private final ControlledRealTimeReopenThread<IndexSearcher> nrtReopenThread;

    public TransactionalUpdateExample() throws Exception {
        Directory dir = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig();

        // 1. Create IndexWriter
        this.indexWriter = new IndexWriter(dir, config);

        // 2. Create SearcherManager (wraps the writer for NRT)
        // Passing 'true' for applyAllDeletes ensures deletions are also visible
        this.searcherManager = new SearcherManager(indexWriter, true, true, new SearcherFactory());

        // 3. Create ControlledRealTimeReopenThread
        // maxStaleSec (5.0): Max time a searcher can be stale if nobody is waiting
        // minStaleSec (0.025): Min time between refreshes when someone IS waiting
        this.nrtReopenThread = new ControlledRealTimeReopenThread<>(
            indexWriter,
            searcherManager,
            5.0,   // Target max stale (seconds)
            0.025  // Target min stale (seconds)
        );

        // Start the background thread
        this.nrtReopenThread.start();
    }

    public void updateAndMakeVisible(Document doc) throws InterruptedException, java.io.IOException {
        // A. Perform the update and capture the generation ID
        long generation = indexWriter.addDocument(doc);

        // B. Block until this specific generation is visible
        // This triggers a refresh if needed and waits
        nrtReopenThread.waitForGeneration(generation);

        // C. At this line, the update is guaranteed to be in the current searcher
    }

    public void search() throws java.io.IOException {
        // D. Acquire the searcher (it will be up-to-date)
        IndexSearcher searcher = searcherManager.acquire();
        try {
            // Execute search...
            System.out.println("Searcher matches: " + searcher.count(new org.apache.lucene.search.MatchAllDocsQuery()));
        } finally {
            searcherManager.release(searcher);
        }
    }

    public void close() throws java.io.IOException {
        nrtReopenThread.close();
        searcherManager.close();
        indexWriter.close();
    }
}

```
## Making searches visible, eventually
Probably the best idea is periodically flush, based on RAM. For operations where we don't care WHEN a flush happens.
```java
IndexWriterConfig config = new IndexWriterConfig();
// Sets the maximum memory (in MB) used for buffering added documents
// and deletions before they are flushed to the Directory.
// Default is 16.0 MB. 256.0 MB is common for high-throughput indexing.
config.setRAMBufferSizeMB(256.0);
```
