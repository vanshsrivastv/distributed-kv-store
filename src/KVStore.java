import java.io.IOException;
import java.util.Scanner;

public class KVStore {

    private static final String WAL_FILE = "store.wal";

    private final SkipList memtable;
    private final WriteAheadLog wal;

    public KVStore() throws IOException {

        memtable = new SkipList();
        wal = new WriteAheadLog(WAL_FILE);

        wal.recover(memtable);
    }

    public void put(String key, String value) throws IOException {

        wal.appendPut(key, value);
        memtable.put(key, value);
    }

    public String get(String key) {

        return memtable.get(key);
    }

    public void delete(String key) throws IOException {

        wal.appendDelete(key);
        memtable.delete(key);
    }

    public void close() throws IOException {

        wal.close();
    }

    public static void main(String[] args) throws IOException {

        KVStore store = new KVStore();
        Scanner sc = new Scanner(System.in);

        while (true) {

            System.out.print("> ");
            String input = sc.nextLine();
            input = input.trim();

            if (input.equals("exit")) {
                break;
            }

            if (input.isEmpty()) {
                System.out.println("Unknown Command");
                continue;
            }

            String[] parts = input.split("\\s+");
            String command = parts[0];

            if (command.equals("put")) {

                if (parts.length < 3) {
                    System.out.println("Missing arguments");
                } else {
                    store.put(parts[1], parts[2]);
                }

            } else if (command.equals("get")) {

                if (parts.length < 2) {
                    System.out.println("Missing arguments");
                } else {
                    System.out.println(store.get(parts[1]));
                }

            } else if (command.equals("delete")) {

                if (parts.length < 2) {
                    System.out.println("Missing arguments");
                } else {
                    store.delete(parts[1]);
                }

            } else {
                System.out.println("Unknown Command");
            }
        }

        store.close();
        sc.close();
    }
}