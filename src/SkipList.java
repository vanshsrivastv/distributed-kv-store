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

    public String get(String key) {

        Node[] predecessors = findPredecessors(key);

        Node next = predecessors[0].next[0];

        if (next != null && next.key.equals(key)) {

            if (next.tombstone) {
                return null;
            }

            return next.value;
        }

        return null;
    }

    public void delete(String key) {
        upsert(key, null, true);
    }

    public static void main(String[] args) {

        SkipList list = new SkipList();

        list.put("name", "vansh");
        list.put("age", "19");
        list.put("city", "kanpur");

        System.out.println(list.get("name"));
        System.out.println(list.get("age"));
        System.out.println(list.get("city"));
        System.out.println(list.get("college"));

        list.put("name", "rahul");

        System.out.println(list.get("name"));

        list.delete("name");

        System.out.println(list.get("name"));

        list.put("name", "vansh");

        System.out.println(list.get("name"));
    }
}