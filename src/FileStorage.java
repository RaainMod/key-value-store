import java.util.Hashtable;
import java.util.Map;

public class FileStorage {
    private Hashtable<String, byte[]> hashval2Block = new Hashtable<>();

    public synchronized byte[] findBlock(String hashval)
    {
        if(!hashval2Block.containsKey(hashval))
            return null;
        return hashval2Block.get(hashval);
    }
    public synchronized boolean removeEntry(String hashval)
    {
        if(!hashval2Block.containsKey(hashval))
            return false;
        hashval2Block.remove(hashval);
        return true;
    }
    public synchronized void insertEntry(String hashval,byte[]data)
    {
        hashval2Block.put(hashval,data);
    }
    public Hashtable getHashtable()
    {
        return this.hashval2Block;
    }

    public synchronized void clearStorage()
    {
        hashval2Block.clear();
    }
}
