
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

public class SSTableReader {

    private static final byte VALUE = 0;
    private static final byte TOMBSTONE = 1;

    public enum Status {
        FOUND,
        DELETED,
        NOT_FOUND
    }

    public static class Result {

        public final Status status;
        public final String value;

        public Result(Status status, String value) {
            this.status = status;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "status=" + status +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    public static Result get(String filePath, String key)
            throws IOException {

        try (DataInputStream input =
                     new DataInputStream(
                             new FileInputStream(filePath))) {

            while (true) {

                int payloadLength;

                try {
                    payloadLength = input.readInt();
                } catch (EOFException e) {
                    return new Result(Status.NOT_FOUND, null);
                }

                if (payloadLength <= 0) {
                    throw new IOException(
                            "Invalid SSTable record length"
                    );
                }

                byte[] payload = new byte[payloadLength];

                try {
                    input.readFully(payload);
                } catch (EOFException e) {
                    throw new IOException(
                            "Torn SSTable record", e
                    );
                }

                long storedChecksum;

                try {
                    storedChecksum = input.readLong();
                } catch (EOFException e) {
                    throw new IOException(
                            "Torn SSTable record", e
                    );
                }

                CRC32 crc = new CRC32();
                crc.update(payload);

                if (storedChecksum != crc.getValue()) {
                    throw new IOException(
                            "Corrupted SSTable record"
                    );
                }

                try (DataInputStream record =
                             new DataInputStream(
                                     new ByteArrayInputStream(payload))) {

                    byte type = record.readByte();
                    String recordKey = record.readUTF();

                    if (recordKey.equals(key)) {

                        if (type == VALUE) {

                            String value = record.readUTF();

                            return new Result(
                                    Status.FOUND, value
                            );

                        } else if (type == TOMBSTONE) {

                            return new Result(
                                    Status.DELETED, null
                            );

                        } else {
                            throw new IOException(
                                    "Unknown SSTable record type"
                            );
                        }
                    }
                }
            }
        }
    }

    public static List<SkipList.Entry> readAll(String filePath)
            throws IOException {

        List<SkipList.Entry> entries = new ArrayList<>();

        try (DataInputStream input =
                     new DataInputStream(
                             new FileInputStream(filePath))) {

            while (true) {

                int payloadLength;

                try {
                    payloadLength = input.readInt();
                } catch (EOFException e) {
                    break;
                }

                if (payloadLength <= 0) {
                    throw new IOException(
                            "Invalid SSTable record length"
                    );
                }

                byte[] payload = new byte[payloadLength];

                try {
                    input.readFully(payload);
                } catch (EOFException e) {
                    throw new IOException(
                            "Torn SSTable record", e
                    );
                }

                long storedChecksum;

                try {
                    storedChecksum = input.readLong();
                } catch (EOFException e) {
                    throw new IOException(
                            "Torn SSTable record", e
                    );
                }

                CRC32 crc = new CRC32();
                crc.update(payload);

                if (storedChecksum != crc.getValue()) {
                    throw new IOException(
                            "Corrupted SSTable record"
                    );
                }

                try (DataInputStream record =
                             new DataInputStream(
                                     new ByteArrayInputStream(payload))) {

                    byte type = record.readByte();
                    String key = record.readUTF();

                    if (type == VALUE) {

                        String value = record.readUTF();

                        entries.add(
                                new SkipList.Entry(
                                        key, value, false
                                )
                        );

                    } else if (type == TOMBSTONE) {

                        entries.add(
                                new SkipList.Entry(
                                        key, null, true
                                )
                        );

                    } else {
                        throw new IOException(
                                "Unknown SSTable record type"
                        );
                    }
                }
            }
        }

        return entries;
    }

    public static void main(String[] args) throws IOException {

        System.out.println("Reading all records:");

        List<SkipList.Entry> entries =
                readAll("test.sst");

        for (SkipList.Entry entry : entries) {
            System.out.println(entry);
        }

        System.out.println("\nIndividual lookups:");

        System.out.println("apple: " + get("test.sst", "apple"));
        System.out.println("mango: " + get("test.sst", "mango"));
        System.out.println("kiwi: " + get("test.sst", "kiwi"));
    }
}
