
import java.util.ArrayList;
import java.util.List;

public class Compactor {

    public static List<SkipList.Entry> merge(
            List<SkipList.Entry> older,
            List<SkipList.Entry> newer,
            boolean dropTombstones) {

        List<SkipList.Entry> result = new ArrayList<>();

        int i = 0;
        int j = 0;

        while (i < older.size() && j < newer.size()) {

            SkipList.Entry oldEntry = older.get(i);
            SkipList.Entry newEntry = newer.get(j);

            int comparison =
                    oldEntry.key.compareTo(newEntry.key);

            if (comparison < 0) {
                result.add(oldEntry);
                i++;

            } else if (comparison > 0) {
                result.add(newEntry);
                j++;

            } else {
                result.add(newEntry);
                i++;
                j++;
            }
        }

        while (i < older.size()) {
            result.add(older.get(i));
            i++;
        }

        while (j < newer.size()) {
            result.add(newer.get(j));
            j++;
        }

        if (dropTombstones) {
            result.removeIf(entry -> entry.tombstone);
        }

        return result;
    }

    public static void main(String[] args) {

        List<SkipList.Entry> older = new ArrayList<>();

        older.add(new SkipList.Entry("apple", "red", false));
        older.add(new SkipList.Entry("banana", "yellow", false));
        older.add(new SkipList.Entry("mango", "sweet", false));

        List<SkipList.Entry> newer = new ArrayList<>();

        newer.add(new SkipList.Entry("banana", "green", false));
        newer.add(new SkipList.Entry("cherry", "red", false));
        newer.add(new SkipList.Entry("mango", null, true));

        System.out.println("Keeping tombstones:");

        List<SkipList.Entry> keep =
                merge(older, newer, false);

        for (SkipList.Entry entry : keep) {
            System.out.println(entry);
        }

        System.out.println("\nDropping tombstones:");

        List<SkipList.Entry> drop =
                merge(older, newer, true);

        for (SkipList.Entry entry : drop) {
            System.out.println(entry);
        }
    }
}
