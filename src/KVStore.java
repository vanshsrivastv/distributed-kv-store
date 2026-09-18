import java.util.*;

public class KVStore {

    private final Map<String, String> data = new HashMap<>();

    public void put(String key, String value) {
        data.put(key, value);
    }

    public String get(String key) {
        return data.get(key);
    }

    public void delete(String key) {
        data.remove(key);
    }

    public static void main(String[] args) {

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

        sc.close();
    }
}