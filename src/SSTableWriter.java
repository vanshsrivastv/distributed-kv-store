
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.CRC32;

public class SSTableWriter {

    private static final byte VALUE = 0;
    private static final byte TOMBSTONE = 1;

    public static void write(
            String filePath,
            List<SkipList.Entry> entries) throws IOException {

        Path finalPath = Paths.get(filePath);
        Path temporaryPath = Paths.get(filePath + ".tmp");

        try {

            try (FileOutputStream fileOutput =
                         new FileOutputStream(temporaryPath.toFile());

                 DataOutputStream output =
                         new DataOutputStream(fileOutput)) {

                for (SkipList.Entry entry : entries) {

                    byte[] payload = buildPayload(entry);

                    CRC32 crc = new CRC32();
                    crc.update(payload);

                    long checksum = crc.getValue();

                    output.writeInt(payload.length);
                    output.write(payload);
                    output.writeLong(checksum);
                }

                output.flush();
                fileOutput.getFD().sync();
            }

            Files.move(
                    temporaryPath,
                    finalPath,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (IOException e) {
            throw e;
        }
    }

    private static byte[] buildPayload(
            SkipList.Entry entry) throws IOException {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        try (DataOutputStream output =
                     new DataOutputStream(buffer)) {

            if (entry.tombstone) {

                output.writeByte(TOMBSTONE);
                output.writeUTF(entry.key);

            } else {

                output.writeByte(VALUE);
                output.writeUTF(entry.key);
                output.writeUTF(entry.value);
            }
        }

        return buffer.toByteArray();
    }

    public static void main(String[] args) throws IOException {

        SkipList list = new SkipList();

        list.put("zebra", "animal");
        list.put("apple", "fruit");
        list.put("mango", "fruit");
        list.put("banana", "fruit");

        list.delete("mango");

        SSTableWriter.write("test.sst", list.entries());

        System.out.println("SSTable written.");
    }
}
