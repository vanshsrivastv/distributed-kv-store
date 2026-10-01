import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SkipList {

    private static final int MAX_LEVEL = 16;

    private static class Node {

        String key;
        String value;
        boolean tombstone;
        Node[] next;

        Node(String key, String value, int height, boolean tombstone) {
            this.key = key;
            this.value = value;
            this.tombstone = tombstone;
            this.next = new Node[height];
        }
    }

    public static class Entry {

        public final String key;
        public final String value;
        public final boolean tombstone;

        public Entry(String key, String value, boolean tombstone) {
            this.key = key;
            this.value = value;
            this.tombstone = tombstone;
        }

        @Override
        public String toString() {
            return "Entry{" +
                    "key='" + key + '\'' +
                    ", value='" + value + '\'' +
                    ", tombstone=" + tombstone +
                    '}';
        }
    }

    public enum Status {
        FOUND,
        DELETED,
        NOT_FOUND
    }

    public static class LookupResult {

        public final Status status;
        public final String value;

        public LookupResult(Status status, String value) {
            this.status = status;
            this.value = value;
        }

        @Override
        public String toString() {
            return "LookupResult{" +
                    "status=" + status +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    private final Random random = new Random();

    private final Node head = new Node(null, null, MAX_LEVEL, false);

    private int randomHeight() {

        int height = 1;

        while (height < MAX_LEVEL && random.nextBoolean()) {
            height++;
        }

        return height;
    }

    private Node[] findPredecessors(String key) {

        Node[] predecessors = new Node[MAX_LEVEL];

        Node current = head;

        for (int level = MAX_LEVEL - 1; level >= 0; level--) {

            while (current.next[level] != null
                    && current.next[level].key.compareTo(key) < 0) {
                current = current.next[level];
            }

            predecessors[level] = current;
        }

        return predecessors;
    }

    private void upsert(String key, String value, boolean tombstone) {

        Node[] predecessors = findPredecessors(key);

        Node next = predecessors[0].next[0];

        if (next != null && next.key.equals(key)) {
            next.value = value;
            next.tombstone = tombstone;
            return;
        }

        int height = randomHeight();

        Node newNode = new Node(key, value, height, tombstone);

        for (int level = 0; level < height; level++) {
            newNode.next[level] = predecessors[level].next[level];
            predecessors[level].next[level] = newNode;
        }
    }

    public void put(String key, String value) {
        upsert(key, value, false);
    }

    public LookupResult lookup(String key) {

        Node[] predecessors = findPredecessors(key);

        Node next = predecessors[0].next[0];

        if (next == null || !next.key.equals(key)) {
            return new LookupResult(Status.NOT_FOUND, null);
        }

        if (next.tombstone) {
            return new LookupResult(Status.DELETED, null);
        }

        return new LookupResult(Status.FOUND, next.value);
    }

    public String get(String key) {

        LookupResult result = lookup(key);

        if (result.status == Status.FOUND) {
            return result.value;
        }

        return null;
    }

    public void delete(String key) {
        upsert(key, null, true);
    }

    public List<Entry> entries() {

        List<Entry> result = new ArrayList<>();

        Node current = head.next[0];

        while (current != null) {

            result.add(
                    new Entry(
                            current.key,
                            current.value,
                            current.tombstone
                    )
            );

            current = current.next[0];
        }

        return result;
    }

    public static void main(String[] args) {

        SkipList list = new SkipList();

        list.put("zebra", "animal");
        list.put("apple", "fruit");
        list.put("mango", "fruit");
        list.put("banana", "fruit");

        list.delete("mango");

        System.out.println("apple: " + list.lookup("apple"));
        System.out.println("mango: " + list.lookup("mango"));
        System.out.println("kiwi: " + list.lookup("kiwi"));
    }
}