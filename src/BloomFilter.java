import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class BloomFilter {

    private final boolean[] bits;
    private final int numHashes;

    public BloomFilter(int size, int numHashes) {
        bits = new boolean[size];
        this.numHashes = numHashes;
    }

    public static int h1(String key) {
        return key.hashCode();
    }

    public static long h2(String key) {

        long hash = 0xcbf29ce484222325L;
        long prime = 1099511628211L;

        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);

        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= prime;
        }

        return hash;
    }

    public void add(String key) {

        long hash1 = h1(key);
        long hash2 = h2(key);

        for (int i = 0; i < numHashes; i++) {

            long combinedHash =
                    hash1 + (long) i * hash2;

            int index =
                    (int) Math.floorMod(
                            combinedHash,
                            (long) bits.length
                    );

            bits[index] = true;
        }
    }

    public boolean mightContain(String key) {

        long hash1 = h1(key);
        long hash2 = h2(key);

        for (int i = 0; i < numHashes; i++) {

            long combinedHash =
                    hash1 + (long) i * hash2;

            int index =
                    (int) Math.floorMod(
                            combinedHash,
                            (long) bits.length
                    );

            if (!bits[index]) {
                return false;
            }
        }

        return true;
    }

    public static BloomFilter fromEntries(
            List<SkipList.Entry> entries) {

        int size = Math.max(64, entries.size() * 10);

        BloomFilter filter =
                new BloomFilter(size, 3);

        for (SkipList.Entry entry : entries) {
            filter.add(entry.key);
        }

        return filter;
    }

    public void save(String filePath) throws IOException {

        try (DataOutputStream output =
                     new DataOutputStream(
                             new FileOutputStream(filePath))) {

            output.writeInt(bits.length);
            output.writeInt(numHashes);

            for (boolean bit : bits) {
                output.writeBoolean(bit);
            }
        }
    }

    public static BloomFilter load(String filePath)
            throws IOException {

        try (DataInputStream input =
                     new DataInputStream(
                             new FileInputStream(filePath))) {

            int size = input.readInt();
            int numHashes = input.readInt();

            BloomFilter filter =
                    new BloomFilter(size, numHashes);

            for (int i = 0; i < size; i++) {
                filter.bits[i] = input.readBoolean();
            }

            return filter;
        }
    }

    public static void main(String[] args) throws IOException {

    BloomFilter filter =
            BloomFilter.load("test.bloom");

    System.out.println("apple: " +
            filter.mightContain("apple"));

    System.out.println("banana: " +
            filter.mightContain("banana"));

    System.out.println("mango: " +
            filter.mightContain("mango"));

    System.out.println("grape: " +
            filter.mightContain("grape"));

    System.out.println("zebra: " +
            filter.mightContain("zebra"));

    System.out.println("orange: " +
            filter.mightContain("orange"));
}
}