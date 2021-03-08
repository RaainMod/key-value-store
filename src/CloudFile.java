import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

// represent a file that stored on cloud server
public class CloudFile {
    private final String writePath =".\\WriteData\\";
    private  String fileName ;
    private  int fileVersion ;
    private boolean IN_FILE_TRANSFER ;   //check if the server is in file transfer
    private Vector<String> fileInfo;  //store version and hash values
    private Hashtable<String,byte[]> map2blocks = new Hashtable<>();  // records the map from block hash values to block

    public CloudFile(String filename,int version,Vector<String> fileinfo)
    {
        this.IN_FILE_TRANSFER = false;
        this.fileName = filename;
        this.fileVersion = version;
        this.fileInfo = new Vector<>(fileinfo);
    }

    public synchronized boolean rename(String newname)
    {
        this.fileName = newname;
        return true;
    }
    public Vector<String> getfileino()
    {
        return this.fileInfo;
    }

    public void storeblock(byte[] blockdata)
    {
       String hash = SHA_256.hash(blockdata);
       map2blocks.put(hash,blockdata);
    }

    public byte[] getblockdata(String hashvalue)
    {
        if(this.IN_FILE_TRANSFER) // should not transfer data to client
            return null;
        return this.map2blocks.get(hashvalue);
    }
    // clear previous storage and ready to store updated file
    public void clearStorage()
    {
        this.map2blocks.clear();
    }
    public synchronized boolean updatefileinfo(int clientVersion, Vector<String> clientHashList)
    {
        this.IN_FILE_TRANSFER = true;   // prepare for file transfer
        /*
         this probably need some rescue way to avoid that the client update the fileinfo but not being able to
         upload the file and release the lock.
         a possible solution is that release the lock for every specific interval and set time count to 0 when lock
        */
        if(clientVersion == this.fileVersion +1)
        {  // update clould file
            this.fileVersion ++;
            this.fileInfo = clientHashList;
            return true;
        }
        else  // should not update cloud file
            return false;
    }
    public void releaselock()
    {
        this.IN_FILE_TRANSFER = false;
    }

    public void writeToDisk()
    {
        File dir = new File(writePath);
        if(!dir.exists())
            dir.mkdirs();
        String filePath = writePath+ this.fileName;
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            for(int i=1;i<fileInfo.size();i++)
            {
                String hashvalue = fileInfo.get(i);
                fos.write(map2blocks.get(hashvalue));
            }
            fos.close();
        }catch (IOException e)
        {
            e.printStackTrace();
        }

    }


}
