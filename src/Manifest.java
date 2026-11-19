
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public class Manifest {

    private static final int CHECKSUM_SIZE = Long.BYTES;

    public static void save(
            String path,
            List<SSTableMeta> metas) throws IOException {

        Path finalPath = Paths.get(path);
        Path temporaryPath = Paths.get(path + ".tmp");

        Path parent = finalPath.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(metas.size());

            for (SSTableMeta meta : metas) {
                output.writeUTF(meta.getFileName());
                output.writeInt(meta.getLevel());
                output.writeUTF(meta.getMinKey());
                output.writeUTF(meta.getMaxKey());
            }
        }

        byte[] payload = buffer.toByteArray();

        CRC32 crc = new CRC32();
        crc.update(payload);

        try {
            try (FileOutputStream fileOutput =
                         new FileOutputStream(temporaryPath.toFile());
                 DataOutputStream output =
                         new DataOutputStream(fileOutput)) {

                output.write(payload);
                output.writeLong(crc.getValue());
                output.flush();
                fileOutput.getFD().sync();
            }

            Files.move(
                    temporaryPath,
                    finalPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }

            throw e;
        }
    }

    public static List<SSTableMeta> load(
            String path) throws IOException {

        Path filePath = Paths.get(path);

        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }

        byte[] fileBytes = Files.readAllBytes(filePath);

        if (fileBytes.length < Integer.BYTES + CHECKSUM_SIZE) {
            throw new IOException("Manifest is too short or corrupted");
        }

        int payloadLength = fileBytes.length - CHECKSUM_SIZE;

        CRC32 crc = new CRC32();
        crc.update(fileBytes, 0, payloadLength);

        long storedChecksum;

        try (DataInputStream checksumInput = new DataInputStream(
                new ByteArrayInputStream(
                        fileBytes,
                        payloadLength,
                        CHECKSUM_SIZE))) {

            storedChecksum = checksumInput.readLong();
        }

        if (crc.getValue() != storedChecksum) {
            throw new IOException("Manifest checksum mismatch");
        }

        List<SSTableMeta> metas = new ArrayList<>();

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(fileBytes, 0, payloadLength))) {

            int count = input.readInt();

            if (count < 0) {
                throw new IOException("Invalid manifest record count");
            }

            for (int i = 0; i < count; i++) {
                String fileName = input.readUTF();
                int level = input.readInt();
                String minKey = input.readUTF();
                String maxKey = input.readUTF();

                try {
                    metas.add(new SSTableMeta(
                            fileName, level, minKey, maxKey));
                } catch (IllegalArgumentException e) {
                    throw new IOException(
                            "Invalid metadata record in manifest", e);
                }
            }

            if (input.available() != 0) {
                throw new IOException(
                        "Unexpected extra data in manifest");
            }

        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid manifest contents", e);
        }

        return metas;
    }
}
