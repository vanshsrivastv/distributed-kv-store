import java.io.IOException;

public class CrashRecoveryTest {

    private static final String WAL_FILE = "wal.log";

    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.out.println("Use: run1 or run2");
            return;
        }

        if (args[0].equals("run1")) {
            run1();
        } else if (args[0].equals("run2")) {
            run2();
        } else {
            System.out.println("Use: run1 or run2");
        }
    }

    private static void run1() throws IOException {

        SkipList list = new SkipList();
        WriteAheadLog wal = new WriteAheadLog(WAL_FILE);

        wal.appendPut("name", "vansh");
        list.put("name", "vansh");

        wal.appendPut("age", "19");
        list.put("age", "19");

        wal.appendPut("city", "kanpur");
        list.put("city", "kanpur");

        wal.appendDelete("age");
        list.delete("age");

        System.out.println("Run 1 complete.");
        System.out.println("Simulating crash...");
    }

    private static void run2() throws IOException {

        SkipList list = new SkipList();
        WriteAheadLog wal = new WriteAheadLog(WAL_FILE);

        wal.recover(list);

        System.out.println("name: " + list.get("name"));
        System.out.println("age: " + list.get("age"));
        System.out.println("city: " + list.get("city"));

        wal.close();
    }
}