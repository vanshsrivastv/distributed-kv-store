import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

public class WriteAheadLog {

    private static final byte PUT = 0;
    private static final byte DELETE = 1;

    private FileOutputStream output;
    private DataOutputStream dataOutput;
    private final String filePath;

    public WriteAheadLog(String filePath) throws IOException {
        this.filePath = filePath;
        this.output = new FileOutputStream(filePath, true);
        this.dataOutput = new DataOutputStream(output);
    }

    public void appendPut(String key, String value) throws IOException {

        byte[] payload = buildPutPayload(key, value);
        append(payload);
    }

    public void appendDelete(String key) throws IOException {

        byte[] payload = buildDeletePayload(key);
        append(payload);
    }

    private void append(byte[] payload) throws IOException {

        CRC32 crc = new CRC32();
        crc.update(payload);

        long checksum = crc.getValue();

        dataOutput.writeInt(payload.length);
        dataOutput.write(payload);
        dataOutput.writeLong(checksum);

        output.getFD().sync();
    }

    private byte[] buildPutPayload(String key, String value)
            throws IOException {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        DataOutputStream dataOutput =
                new DataOutputStream(buffer);

        dataOutput.writeByte(PUT);
        dataOutput.writeUTF(key);
        dataOutput.writeUTF(value);

        dataOutput.close();

        return buffer.toByteArray();
    }

    private byte[] buildDeletePayload(String key)
            throws IOException {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        DataOutputStream dataOutput =
                new DataOutputStream(buffer);

        dataOutput.writeByte(DELETE);
        dataOutput.writeUTF(key);

        dataOutput.close();

        return buffer.toByteArray();
    }

    public void recover(SkipList list) throws IOException {

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

                byte[] payload = new byte[payloadLength];

                try {
                    input.readFully(payload);
                } catch (EOFException e) {
                    System.out.println("Torn WAL record detected.");
                    break;
                }

                long storedChecksum;

                try {
                    storedChecksum = input.readLong();
                } catch (EOFException e) {
                    System.out.println("Torn WAL record detected.");
                    break;
                }

                CRC32 crc = new CRC32();
                crc.update(payload);

                long calculatedChecksum = crc.getValue();

                if (storedChecksum != calculatedChecksum) {
                    System.out.println("Corrupted WAL record detected.");
                    break;
                }

                applyPayload(list, payload);
            }
        }
    }

    private void applyPayload(SkipList list, byte[] payload)
            throws IOException {

        DataInputStream input =
                new DataInputStream(
                        new ByteArrayInputStream(payload));

        byte type = input.readByte();

        if (type == PUT) {

            String key = input.readUTF();
            String value = input.readUTF();

            list.put(key, value);

        } else if (type == DELETE) {

            String key = input.readUTF();

            list.delete(key);

        } else {

            System.out.println("Unknown WAL record type.");
        }

        input.close();
    }

    public void truncate() throws IOException {

        dataOutput.close();

        File file = new File(filePath);

        if (!file.delete()) {
            throw new IOException("Failed to delete WAL file");
        }

        output = new FileOutputStream(filePath, true);
        dataOutput = new DataOutputStream(output);
    }

    public void close() throws IOException {
        dataOutput.close();
    }
}