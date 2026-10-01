import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KVStore {

    private static final String WAL_FILE = "store.wal";
    private static final long FLUSH_THRESHOLD = 30;

    private static final Pattern SSTABLE_PATTERN =
            Pattern.compile("sstable-(\\d+)\\.sst");

    private SkipList memtable;
    private final WriteAheadLog wal;

    private long approxBytes = 0;
    private int sstableCounter = 0;

    private final List<String> sstableFiles =
            new ArrayList<>();

    private final Map<String, BloomFilter> bloomFilters =
            new HashMap<>();

    public KVStore() throws IOException {

        memtable = new SkipList();

        loadSSTables();

        wal = new WriteAheadLog(WAL_FILE);

        wal.recover(memtable);
    }

    private void loadSSTables() {

        File directory = new File(".");

        File[] files = directory.listFiles(
                (dir, name) ->
                        SSTABLE_PATTERN.matcher(name).matches()
        );

        if (files == null) {
            return;
        }

        Arrays.sort(
                files,
                Comparator.comparingInt(
                        file -> extractSSTableNumber(file.getName())
                )
        );

        int highestNumber = -1;

        for (File file : files) {

            String filePath = file.getName();

            sstableFiles.add(filePath);

            int number =
                    extractSSTableNumber(filePath);

            if (number > highestNumber) {
                highestNumber = number;
            }

            String bloomPath =
                    filePath + ".bloom";

            try {

                BloomFilter filter =
                        BloomFilter.load(bloomPath);

                bloomFilters.put(filePath, filter);

            } catch (IOException e) {

                System.out.println(
                        "Bloom filter unavailable for "
                                + filePath
                );
            }
        }

        sstableCounter = highestNumber + 1;
    }

    private int extractSSTableNumber(String fileName) {

        Matcher matcher =
                SSTABLE_PATTERN.matcher(fileName);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid SSTable filename: " + fileName
            );
        }

        return Integer.parseInt(matcher.group(1));
    }

    public void put(String key, String value)
            throws IOException {

        wal.appendPut(key, value);

        memtable.put(key, value);

        approxBytes += key.length() + value.length();

        if (approxBytes >= FLUSH_THRESHOLD) {
            flush();
        }
    }

    public String get(String key) throws IOException {

        SkipList.LookupResult memResult =
                memtable.lookup(key);

        if (memResult.status == SkipList.Status.FOUND) {
            return memResult.value;
        }

        if (memResult.status == SkipList.Status.DELETED) {
            return null;
        }

        for (int i = sstableFiles.size() - 1;
             i >= 0;
             i--) {

            String filePath =
                    sstableFiles.get(i);

            BloomFilter filter =
                    bloomFilters.get(filePath);

            if (filter != null
                    && !filter.mightContain(key)) {
                continue;
            }

            SSTableReader.Result result =
                    SSTableReader.get(filePath, key);

            if (result.status ==
                    SSTableReader.Status.FOUND) {

                return result.value;
            }

            if (result.status ==
                    SSTableReader.Status.DELETED) {

                return null;
            }
        }

        return null;
    }

    public void delete(String key)
            throws IOException {

        wal.appendDelete(key);

        memtable.delete(key);

        approxBytes += key.length();

        if (approxBytes >= FLUSH_THRESHOLD) {
            flush();
        }
    }

    private void flush() throws IOException {

        List<SkipList.Entry> entries =
                memtable.entries();

        String filePath =
                "sstable-" + sstableCounter + ".sst";

        SSTableWriter.write(
                filePath,
                entries
        );

        wal.truncate();

        BloomFilter filter =
                BloomFilter.fromEntries(entries);

        String bloomPath =
                filePath + ".bloom";

        filter.save(bloomPath);

        bloomFilters.put(
                filePath,
                filter
        );

        sstableFiles.add(filePath);

        sstableCounter++;

        memtable = new SkipList();

        approxBytes = 0;

        System.out.println(
                "Flushed: " + filePath
        );
    }

    public void close() throws IOException {
        wal.close();
    }

    public static void main(String[] args)
            throws IOException {

        KVStore store = new KVStore();

        Scanner sc = new Scanner(System.in);

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

            String[] parts =
                    input.split("\\s+");

            String command = parts[0];

            if (command.equals("put")) {

                if (parts.length < 3) {
                    System.out.println(
                            "Missing arguments"
                    );
                } else {
                    store.put(
                            parts[1],
                            parts[2]
                    );
                }

            } else if (command.equals("get")) {

                if (parts.length < 2) {
                    System.out.println(
                            "Missing arguments"
                    );
                } else {

                    String value =
                            store.get(parts[1]);

                    System.out.println(value);
                }

            } else if (command.equals("delete")) {

                if (parts.length < 2) {
                    System.out.println(
                            "Missing arguments"
                    );
                } else {
                    store.delete(parts[1]);
                }

            } else {
                System.out.println(
                        "Unknown Command"
                );
            }
        }

        sc.close();
        store.close();
    }
}