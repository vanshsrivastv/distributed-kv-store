import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class WriteAheadLog {

    private final FileOutputStream output;
    private final String filePath;

    public WriteAheadLog(String filePath) throws IOException {
        this.filePath = filePath;
        this.output = new FileOutputStream(filePath, true);
    }

    private void append(String record) throws IOException {

        output.write(record.getBytes(StandardCharsets.UTF_8));
        output.getFD().sync();
    }

    public void appendPut(String key, String value) throws IOException {

        String record = "PUT " + key + " " + value + System.lineSeparator();

        append(record);
    }

    public void appendDelete(String key) throws IOException {

        String record = "DELETE " + key + System.lineSeparator();

        append(record);
    }

    public void recover(SkipList list) throws IOException {

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {

            String line;

            while ((line = reader.readLine()) != null) {

                String[] parts = line.trim().split("\\s+");

                if (parts[0].equals("PUT")) {

                    if (parts.length != 3) {
                        break;
                    }

                    list.put(parts[1], parts[2]);

                } else if (parts[0].equals("DELETE")) {

                    if (parts.length != 2) {
                        break;
                    }

                    list.delete(parts[1]);

                } else {
                    break;
                }
            }
        }
    }

    public void close() throws IOException {
        output.close();
    }
}