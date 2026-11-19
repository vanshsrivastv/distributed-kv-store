
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ManifestTest {

    public static void main(String[] args) throws IOException {

        String path = "manifest-test.bin";
        Path file = Paths.get(path);

        List<SSTableMeta> original = new ArrayList<>();

        original.add(new SSTableMeta(
                "sstable-0.sst", 0, "apple", "mango"));

        original.add(new SSTableMeta(
                "sstable-1.sst", 0, "banana", "orange"));

        original.add(new SSTableMeta(
                "sstable-2.sst", 1, "grape", "watermelon"));

        try {
            Manifest.save(path, original);

            List<SSTableMeta> loaded = Manifest.load(path);

            if (!original.equals(loaded)) {
                throw new AssertionError(
                        "Loaded metadata does not match saved metadata");
            }

            System.out.println("Round-trip test passed.");
            System.out.println("Loaded metadata:");

            for (SSTableMeta meta : loaded) {
                System.out.println(meta);
            }

            byte[] bytes = Files.readAllBytes(file);
            bytes[0] ^= 1;
            Files.write(file, bytes);

            try {
                Manifest.load(path);
                throw new AssertionError(
                        "Corrupted manifest was incorrectly accepted");
            } catch (IOException expected) {
                System.out.println(
                        "Corruption test passed: "
                                + expected.getMessage());
            }

        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(Paths.get(path + ".tmp"));
        }
    }
}
    