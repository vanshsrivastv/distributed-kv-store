import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

public class BinaryWALTest {

    private static final String FILE = "binary.log";

    private static final byte PUT = 0;
    private static final byte DELETE = 1;

    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.out.println("Use: write or read");
            return;
        }

        if (args[0].equals("write")) {
            write();
        } else if (args[0].equals("read")) {
            read();
        } else {
            System.out.println("Use: write or read");
        }
    }

    private static void write() throws IOException {

        byte[] payload = buildPayload();

        CRC32 crc = new CRC32();
        crc.update(payload);
        long checksum = crc.getValue();

        DataOutputStream output =
                new DataOutputStream(new FileOutputStream(FILE));

        output.writeInt(payload.length);
        output.write(payload);
        output.writeLong(checksum);

        output.close();

        System.out.println("Write complete.");
    }

    private static void read() throws IOException {

        DataInputStream input =
                new DataInputStream(new FileInputStream(FILE));

        int payloadLength = input.readInt();

        byte[] buffer = new byte[payloadLength];
        input.readFully(buffer);

        long storedChecksum = input.readLong();

        input.close();

        CRC32 crc = new CRC32();
        crc.update(buffer);

        long calculatedChecksum = crc.getValue();

        boolean checksumMatched =
                storedChecksum == calculatedChecksum;

        System.out.println("Checksum matched: " + checksumMatched);

        if (checksumMatched) {

            DataInputStream payloadInput =
                    new DataInputStream(
                            new ByteArrayInputStream(buffer));

            byte type = payloadInput.readByte();
            String key = payloadInput.readUTF();
            String value = payloadInput.readUTF();

            payloadInput.close();

            System.out.println("Type: " + type);
            System.out.println("Key: " + key);
            System.out.println("Value: " + value);
        }
    }

    private static byte[] buildPayload() throws IOException {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        DataOutputStream output =
                new DataOutputStream(buffer);

        output.writeByte(PUT);
        output.writeUTF("name");
        output.writeUTF("vansh");

        output.close();

        return buffer.toByteArray();
    }
}