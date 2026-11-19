
import java.util.List;

public class SSTableMeta {

    private final String fileName;
    private final int level;
    private final String minKey;
    private final String maxKey;

    public SSTableMeta(
            String fileName,
            int level,
            String minKey,
            String maxKey) {

        if (fileName == null || minKey == null || maxKey == null) {
            throw new IllegalArgumentException("Metadata fields cannot be null");
        }

        if (level < 0) {
            throw new IllegalArgumentException("Level cannot be negative");
        }

        if (minKey.compareTo(maxKey) > 0) {
            throw new IllegalArgumentException("minKey cannot exceed maxKey");
        }

        this.fileName = fileName;
        this.level = level;
        this.minKey = minKey;
        this.maxKey = maxKey;
    }

    public static SSTableMeta fromEntries(
            String fileName,
            int level,
            List<SkipList.Entry> entries) {

        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot create metadata for an empty SSTable");
        }

        return new SSTableMeta(
                fileName,
                level,
                entries.get(0).key,
                entries.get(entries.size() - 1).key
        );
    }

    public String getFileName() {
        return fileName;
    }

    public int getLevel() {
        return level;
    }

    public String getMinKey() {
        return minKey;
    }

    public String getMaxKey() {
        return maxKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof SSTableMeta)) {
            return false;
        }

        SSTableMeta other = (SSTableMeta) obj;

        return level == other.level
                && fileName.equals(other.fileName)
                && minKey.equals(other.minKey)
                && maxKey.equals(other.maxKey);
    }

    @Override
    public int hashCode() {
        int result = fileName.hashCode();
        result = 31 * result + level;
        result = 31 * result + minKey.hashCode();
        result = 31 * result + maxKey.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SSTableMeta{"
                + "fileName='" + fileName + '\''
                + ", level=" + level
                + ", minKey='" + minKey + '\''
                + ", maxKey='" + maxKey + '\''
                + '}';
    }
}
