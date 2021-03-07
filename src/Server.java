import org.apache.xmlrpc.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;



public class Server {
	// this Hashtable maps the filename to it's cloud file structure
	private Map<String,CloudFile> cloudFileMap;

	// three different status of cluster server
	enum Status{FOLLOWER,CANDIDATE,LEADER};
	// initial status of a server
	private Status mystatus;
	// server log file
	private LogFile logFile;
	// other servers' information
	private AdjacentNodes adjNodes = new AdjacentNodes(); // 构建参数还没有考虑清楚
	// server timeout value
	private Timer electionTimer;
	private  int  timeoutValue;

	class TimeoutTask extends TimerTask
	{
		@Override
		public void run()
		{
			mystatus = Status.CANDIDATE;
			electionTimer.cancel();
			electionTimer = new Timer();
			Random rand = new Random();
			timeoutValue = rand.nextInt(151)+150;
			electionTimer.schedule(new TimeoutTask(),timeoutValue);

			RequestVote();

		}
	}


	//default construct function
	public Server()
	{
		this.cloudFileMap = new Hashtable<>();
		this.mystatus = Status.FOLLOWER;
		this.logFile = new LogFile();
		Random rand = new Random();
		this.timeOut = rand.nextInt(151)+150;  //produce a random in [150,300] as the initial timeout value
	}

	public void SetTimeOut()
	{
		Random rand = new Random();
		this.timeOut = rand.nextInt(151)+150;
	}
	public boolean RequestVote(int candTerm,String candSocket,)
	{
		this.mystatus = Status.CANDIDATE;
		return true;
	}








	/*======================================================== Proj 2 Codes ======================================================================*/

	// A simple ping, simply returns True
	public boolean ping() {
		System.out.println("Ping()");
		return true;
	}

	// Given a hash value, return the associated block
	public byte[] getblock(String hashvalue,String fileName) {
		System.out.println("GetBlock()");
		// get block directly from hashtable
		CloudFile cf = cloudFileMap.get(fileName);
		if(cf == null)
			System.out.println("Error!");
		byte[] blockData = cf.getblockdata(hashvalue);
		return blockData;
	}

	// Store the provided block
	public boolean putblock(String fileName,byte[] blockData) {
		System.out.println("PutBlock()");
		CloudFile cf = cloudFileMap.get(fileName);
		cf.storeblock(blockData);
		return true;
	}

	// Determine which of the provided blocks are on this server
	// never used in this implementation
	public Vector hasblocks(Vector hashlist) {
		System.out.println("HasBlocks()");

		return hashlist;
	}



	// Returns the server's FileInfoMap
	public Vector<String> getfileinfomap(String fileName) {
		System.out.println("GetFileInfoMap()");
	 	if(!cloudFileMap.containsKey(fileName)) {
	 		System.out.println("Fie:"+fileName+" does not exist on cloud.");
			return new Vector<String>();
		}
	 	Vector<String> result = cloudFileMap.get(fileName).getfileino();
		return result;
	}

	//Return names of all files that store on this cloud
	public Vector<String> getallfilenames()
	{
		System.out.println("Get all files names, including:");
		Vector<String> fileNames = new Vector<>();
		for(String key: cloudFileMap.keySet()) {
			System.out.println(key);
			fileNames.add(key);
		}
		return fileNames;
	}

	// Update's the given entry in the fileinfomap
	public synchronized boolean updatefile(String filename, int version, Vector<String> fileInfo) {
		System.out.println("Try to UpdateFile(" + filename + ")");
		if(!cloudFileMap.containsKey(filename))
		{

			CloudFile newFile = new CloudFile(filename,version-1,fileInfo);  //use version-1 because we need the upload file version to be 1 larger than cloud edition
			cloudFileMap.put(filename,newFile);
		}
		CloudFile cf = cloudFileMap.get(filename);
		if(!cf.updatefileinfo(version, fileInfo))
		{
			System.out.println();
			//indicate that file has already been modified by others
			return false;
		}
		// now use the methods that just clear all the old blocks but may cause problem
		cf.clearStorage();
		return true;
	}
	public boolean testFunction(String a1, String a2)
	{
		System.out.println(a1+a2);
		return true;
	}
	public boolean finishtransfer(String fileName,Vector<String> fileInfo)
	{
		System.out.println("Finish the transfer of "+fileName);
		cloudFileMap.get(fileName).releaselock();

		// the clean of old block still needs optimization

//		cloudFileMap.updateStorage(fileInfo);
		return true;
	}
	public boolean writetodisk(String fileName)
	{
		CloudFile cf = cloudFileMap.get(fileName);
		if(cf == null) return false;
		cf.writeToDisk();
		return  true;
	}

	public Vector<String>listfiles()
	{
		Vector<String> vec = new Vector<>();
		for(String s:cloudFileMap.keySet())
			vec.add(s);
		return vec;
	}

	// PROJECT 3 APIs below

	// Queries whether this metadata store is a leader
	// Note that this call should work even when the server is "crashed"
	public boolean isLeader() {
		System.out.println("IsLeader()");
		return true;
	}

	// "Crashes" this metadata store
	// Until Restore() is called, the server should reply to all RPCs
	// with an error (unless indicated otherwise), and shouldn't send
	// RPCs to other servers
	public boolean crash() {
		System.out.println("Crash()");
		return true;
	}

	// "Restores" this metadata store, allowing it to start responding
	// to and sending RPCs to other nodes
	public boolean restore() {
		System.out.println("Restore()");
		return true;
	}

	// "IsCrashed" returns the status of this metadata node (crashed or not)
	// This method should always work, even when the node is crashed
	public boolean isCrashed() {
		System.out.println("IsCrashed()");
		return true;
	}

	public static void main (String [] args) {
		try {

			System.out.println("Attempting to start XML-RPC Server...");

			WebServer server = new WebServer(8080);
			server.addHandler("surfstore", new Server());
			server.start();

			System.out.println("Started successfully.");
			System.out.println("Accepting requests. (Halt program to stop.)");

		} catch (Exception exception){
			System.err.println("Server: " + exception);
		}
	}
}
