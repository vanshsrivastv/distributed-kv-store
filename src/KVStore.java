import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KVStore {

    private static final String WAL_FILE = "store.wal";
    private static final String MANIFEST_FILE = "MANIFEST";
    private static final String MANIFEST_TEMP_FILE = "MANIFEST.tmp";
    private static final long FLUSH_THRESHOLD = 30;

    private static final Pattern SSTABLE_PATTERN =
            Pattern.compile("sstable-(\\d+)\\.sst");

    private static final Pattern BLOOM_PATTERN =
            Pattern.compile("sstable-\\d+\\.sst\\.bloom");

    private SkipList memtable;
    private final WriteAheadLog wal;

    private long approxBytes = 0;
    private int sstableCounter = 0;

    private final List<String> sstableFiles = new ArrayList<>();
    private final List<SSTableMeta> sstableMetas = new ArrayList<>();
    private final Map<String, BloomFilter> bloomFilters = new HashMap<>();

    public KVStore() throws IOException {

        memtable = new SkipList();

        cleanupTemporaryFiles();
        validateManifestPresence();
        loadManifest();
        cleanupOrphanedFiles();

        wal = new WriteAheadLog(WAL_FILE);
        wal.recover(memtable);
    }

    private void cleanupTemporaryFiles() throws IOException {

        File directory = new File(".");

        File[] files = directory.listFiles(
                (dir, name) ->
                        name.equals(MANIFEST_TEMP_FILE)
                                || (name.startsWith("sstable-")
                                && name.endsWith(".tmp"))
        );

        if (files == null) {
            throw new IOException("Unable to list database directory");
        }

        for (File file : files) {
            if (!file.delete()) {
                throw new IOException(
                        "Failed to delete temporary file: "
                                + file.getName()
                );
            }

            System.out.println(
                    "Removed temporary file: " + file.getName()
            );
        }
    }

    private void validateManifestPresence() throws IOException {

        File manifest = new File(MANIFEST_FILE);

        if (manifest.exists()) {
            return;
        }

        File directory = new File(".");
        File[] files = directory.listFiles();

        if (files == null) {
            throw new IOException("Unable to list database directory");
        }

        for (File file : files) {
            if (SSTABLE_PATTERN.matcher(file.getName()).matches()) {
                throw new IOException(
                        "MANIFEST is missing but SSTable files exist. "
                                + "Refusing to start to prevent data loss: "
                                + file.getName()
                );
            }
        }

        // No manifest and no SSTables means this is a brand-new database.
        // Persist an empty manifest so a later missing manifest is suspicious.
        Manifest.save(MANIFEST_FILE, new ArrayList<>());
        System.out.println("Created empty MANIFEST for new database.");
    }

    private void loadManifest() throws IOException {

        List<SSTableMeta> metas = Manifest.load(MANIFEST_FILE);

        int highestNumber = -1;

        for (SSTableMeta meta : metas) {

            String fileName = meta.getFileName();

            if (!SSTABLE_PATTERN.matcher(fileName).matches()) {
                throw new IOException(
                        "Invalid SSTable filename in manifest: " + fileName
                );
            }

            sstableMetas.add(meta);
            sstableFiles.add(fileName);

            int number = extractSSTableNumber(fileName);
            highestNumber = Math.max(highestNumber, number);

            String bloomPath = fileName + ".bloom";

            try {
                BloomFilter filter = BloomFilter.load(bloomPath);
                bloomFilters.put(fileName, filter);

            } catch (IOException e) {
                System.out.println(
                        "Bloom filter unavailable for " + fileName
                                + "; falling back to SSTable scan."
                );
            }
        }

        sstableCounter = highestNumber + 1;
    }

    private void cleanupOrphanedFiles() throws IOException {

        Set<String> liveFiles = new HashSet<>(sstableFiles);

        File directory = new File(".");
        File[] files = directory.listFiles();

        if (files == null) {
            throw new IOException("Unable to list database directory");
        }

        for (File file : files) {

            String name = file.getName();
            boolean isSSTable =
                    SSTABLE_PATTERN.matcher(name).matches();

            boolean isBloomFilter =
                    BLOOM_PATTERN.matcher(name).matches();

            if (!isSSTable && !isBloomFilter) {
                continue;
            }

            String owningSSTable = isBloomFilter
                    ? name.substring(0, name.length() - ".bloom".length())
                    : name;

            if (!liveFiles.contains(owningSSTable)) {

                if (!file.delete()) {
                    throw new IOException(
                            "Failed to delete orphaned file: " + name
                    );
                }

                System.out.println("Removed orphaned file: " + name);
            }
        }
    }

    private int extractSSTableNumber(String fileName) {

        Matcher matcher = SSTABLE_PATTERN.matcher(fileName);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid SSTable filename: " + fileName
            );
        }

        return Integer.parseInt(matcher.group(1));
    }

    public void put(String key, String value) throws IOException {

        wal.appendPut(key, value);
        memtable.put(key, value);

        approxBytes += key.length() + value.length();

        if (approxBytes >= FLUSH_THRESHOLD) {
            flush();
        }
    }

    public String get(String key) throws IOException {

        SkipList.LookupResult memResult = memtable.lookup(key);

        if (memResult.status == SkipList.Status.FOUND) {
            return memResult.value;
        }

        if (memResult.status == SkipList.Status.DELETED) {
            return null;
        }

        for (int i = sstableFiles.size() - 1; i >= 0; i--) {

            String filePath = sstableFiles.get(i);
            BloomFilter filter = bloomFilters.get(filePath);

            if (filter != null && !filter.mightContain(key)) {
                continue;
            }

            SSTableReader.Result result =
                    SSTableReader.get(filePath, key);

            if (result.status == SSTableReader.Status.FOUND) {
                return result.value;
            }

            if (result.status == SSTableReader.Status.DELETED) {
                return null;
            }
        }

        return null;
    }

    public void delete(String key) throws IOException {

        wal.appendDelete(key);
        memtable.delete(key);

        approxBytes += key.length();

        if (approxBytes >= FLUSH_THRESHOLD) {
            flush();
        }
    }

    private void flush() throws IOException {

        List<SkipList.Entry> entries = memtable.entries();

        if (entries.isEmpty()) {
            return;
        }

        String filePath = "sstable-" + sstableCounter + ".sst";

        SSTableWriter.write(filePath, entries);

        BloomFilter filter = BloomFilter.fromEntries(entries);
        filter.save(filePath + ".bloom");

        List<SSTableMeta> updatedMetas =
                new ArrayList<>(sstableMetas);

        updatedMetas.add(
                SSTableMeta.fromEntries(filePath, 0, entries)
        );

        Manifest.save(MANIFEST_FILE, updatedMetas);

        sstableMetas.clear();
        sstableMetas.addAll(updatedMetas);

        sstableFiles.add(filePath);
        bloomFilters.put(filePath, filter);

        sstableCounter++;

        wal.truncate();

        memtable = new SkipList();
        approxBytes = 0;

        System.out.println("Flushed: " + filePath);
    }

    public void compact() throws IOException {

        if (sstableFiles.isEmpty()) {
            System.out.println("No SSTables to compact.");
            return;
        }

        List<String> oldFiles = new ArrayList<>(sstableFiles);

        List<SkipList.Entry> merged =
                SSTableReader.readAll(oldFiles.get(0));

        for (int i = 1; i < oldFiles.size(); i++) {

            List<SkipList.Entry> newer =
                    SSTableReader.readAll(oldFiles.get(i));

            boolean dropTombstones =
                    (i == oldFiles.size() - 1);

            merged = Compactor.merge(
                    merged,
                    newer,
                    dropTombstones
            );
        }

        // A single input file still needs tombstone removal.
        if (oldFiles.size() == 1) {
            merged.removeIf(entry -> entry.tombstone);
        }

        List<SSTableMeta> updatedMetas = new ArrayList<>();
        BloomFilter newFilter = null;
        String newFile = null;

        if (!merged.isEmpty()) {

            newFile = "sstable-" + sstableCounter + ".sst";

            SSTableWriter.write(newFile, merged);

            newFilter = BloomFilter.fromEntries(merged);
            newFilter.save(newFile + ".bloom");

            updatedMetas.add(
                    SSTableMeta.fromEntries(newFile, 0, merged)
            );
        }

        // The manifest switch is the commit point for compaction.
        // Old files are not deleted until the new manifest is durable.
        Manifest.save(MANIFEST_FILE, updatedMetas);

        sstableMetas.clear();
        sstableMetas.addAll(updatedMetas);

        sstableFiles.clear();
        bloomFilters.clear();

        if (newFile != null) {
            sstableFiles.add(newFile);
            bloomFilters.put(newFile, newFilter);
            sstableCounter++;
        }

        // Files absent from the new manifest are now garbage.
        deleteOldSSTables(oldFiles);

        System.out.println(
                "Compaction complete. SSTables remaining: "
                        + sstableFiles.size()
        );
    }

    private void deleteOldSSTables(List<String> oldFiles)
            throws IOException {

        for (String oldFile : oldFiles) {

            File sstable = new File(oldFile);

            if (sstable.exists() && !sstable.delete()) {
                throw new IOException(
                        "Failed to delete " + oldFile
                );
            }

            File bloom = new File(oldFile + ".bloom");

            if (bloom.exists() && !bloom.delete()) {
                throw new IOException(
                        "Failed to delete " + oldFile + ".bloom"
                );
            }
        }
    }

    public void close() throws IOException {
        wal.close();
    }

    public static void main(String[] args) throws IOException {

        KVStore store = new KVStore();
        Scanner sc = new Scanner(System.in);

        try {
            while (true) {

                System.out.print("> ");

                String input = sc.nextLine().trim();

                if (input.equals("exit")) {
                    break;
                }

                if (input.isEmpty()) {
                    System.out.println("Unknown Command");
                    continue;
                }

                String[] parts = input.split("\\s+");
                String command = parts[0];

                if (command.equals("put")) {

                    if (parts.length < 3) {
                        System.out.println("Missing arguments");
                    } else {
                        store.put(parts[1], parts[2]);
                    }

                } else if (command.equals("get")) {

                    if (parts.length < 2) {
                        System.out.println("Missing arguments");
                    } else {
                        System.out.println(store.get(parts[1]));
                    }

                } else if (command.equals("delete")) {

                    if (parts.length < 2) {
                        System.out.println("Missing arguments");
                    } else {
                        store.delete(parts[1]);
                    }

                } else if (command.equals("compact")) {

                    store.compact();

                } else {
                    System.out.println("Unknown Command");
                }
            }

        } finally {
            sc.close();
            store.close();
        }
    }
}
