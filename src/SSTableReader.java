import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
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
                     new DataInputStream(new FileInputStream(filePath))) {

            while (true) {

                int payloadLength;

                try {
                    payloadLength = input.readInt();
                } catch (EOFException e) {
                    return new Result(Status.NOT_FOUND, null);
                }

                byte[] payload = new byte[payloadLength];

                try {
                    input.readFully(payload);
                } catch (EOFException e) {
                    throw new IOException("Torn SSTable record", e);
                }

                long storedChecksum;

                try {
                    storedChecksum = input.readLong();
                } catch (EOFException e) {
                    throw new IOException("Torn SSTable record", e);
                }

                CRC32 crc = new CRC32();
                crc.update(payload);

                long calculatedChecksum = crc.getValue();

                if (storedChecksum != calculatedChecksum) {
                    throw new IOException("Corrupted SSTable record");
                }

                DataInputStream payloadInput =
                        new DataInputStream(
                                new ByteArrayInputStream(payload));

                byte type = payloadInput.readByte();
                String recordKey = payloadInput.readUTF();

                if (recordKey.equals(key)) {

                    if (type == VALUE) {

                        String value = payloadInput.readUTF();
                        payloadInput.close();

                        return new Result(Status.FOUND, value);

                    } else if (type == TOMBSTONE) {

                        payloadInput.close();

                        return new Result(Status.DELETED, null);
                    }
                }

                payloadInput.close();
            }
        }
    }

    public static void main(String[] args) throws IOException {

        Result apple = get("test.sst", "apple");
        Result mango = get("test.sst", "mango");
        Result kiwi = get("test.sst", "kiwi");

        System.out.println("apple: " + apple);
        System.out.println("mango: " + mango);
        System.out.println("kiwi: " + kiwi);
    }
}