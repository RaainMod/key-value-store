import org.apache.xmlrpc.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;


public class Server {

	/*=======================================Member declaration ================================================*/

	private String configPath;
	private String mySocket;
	enum Status{FOLLOWER,CANDIDATE,LEADER};				// three different status of cluster server
	private Status myStatus;              	// initial status of a server

	private LogModule logModule;	// server log file
	private AdjacentNodes adjNodes;		// other servers' information
	private int nodeNumber;       // the number of nodes in this cloud
	private Timer electionTimer;
	private Timer leaderTimer;
	private final int REQ_TIMEOUT = 50;
	private final int LEADER_TIMEOUT = 100;  // every 100ms declare authority to all nodes
	private  int  timeout;		// server timeout value

	private ExecutorService threadPool ;  // thread pool
	private int poolCapacity ;

	private int lastIndex = 0;
	private int lastTerm = 0;
	private int commitIndex = 0;
	private int lastAppliedIndex = 0;
	private int currentTerm = 0;

	private int voteCount ;
	private String voteFor = null;
	private String leaderSocket = null;  //mark the leader socket

	private ReentrantLock voteLock = new ReentrantLock();   // a lock to make sure the vote status is correctly modified
	private ReentrantLock countLock = new ReentrantLock();  // a lock to make sure the voteCount is correctly modified

	private Map<String,Timer> resendTimerMap;        // this maps the node socket to a timer that check if should re-ask the vote to that server
	private Map<String, CloudFile> cloudFileMap; 	// this Hashtable maps the filename to it's cloud file structure

	/*======================================= Methods implementation ================================================== */


	/*--------------------------------------- Server Communication Module ---------------------------------*/
	public Server(String configPath) {
		try {

			this.configPath = configPath;
			File config = new File(configPath);

			String line;
			BufferedReader reader = new BufferedReader(new FileReader(config));
			String[] info;
			line = reader.readLine();
			info = line.split(" ");
			this.mySocket = "127.0.0.1" + ":" + info[0];   // structure : socket+space+servername
			System.out.println("Server name:" + info[1]);
			this.adjNodes = new AdjacentNodes();

			//initialize the information of adjNodes
			this.nodeNumber = 1;
			resendTimerMap = new HashMap<>();
			while((line = reader.readLine())!=null) {
				line.trim();
				resendTimerMap.put(line.substring(line.indexOf(" ")+1),new Timer());  //create a resend timer and bound to the server name
				adjNodes.addNode(line);
				this.nodeNumber ++;
			}
			reader.close();
			this.myStatus = Status.FOLLOWER;
			this.logModule = new LogModule();
			Random rand = new Random();
			this.timeout = rand.nextInt(151) + 150;  //produce a random in [150,300] as the initial timeout value
			this.poolCapacity = 20;
			this.threadPool = Executors.newFixedThreadPool(poolCapacity);

			// start file system
			this.cloudFileMap = new Hashtable<>();
			//start clock
			electionTimer = new Timer();
			leaderTimer = new Timer();
			electionTimer.schedule(new TimeoutTaskForElection(), timeout);


		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	class TimeoutTaskForElection extends TimerTask
	{
		@Override
		public void run()
		{
			electionTimer.cancel();
			// turn to candidate and start vote
			System.out.println("Time up! Staring an election!");
			if(myStatus != Status.LEADER){
				currentTerm ++;
				myStatus = Status.CANDIDATE;
				voteLock.lock();
				voteCount = 0;  // reset the vote count
				voteLock.unlock();

				Random rand = new Random();
				countLock.lock();
				voteCount = 1;
				countLock.unlock();
				if(myStatus == Status.FOLLOWER) {  // otherwise do not need to vote again
					voteLock.lock();
					voteFor = mySocket;  // vote for self
					voteLock.unlock();
				}
				//get all sockets
				Vector<String> sockets = adjNodes.getAllNodeInfo();
				//reset clock

				if(!sockets.isEmpty()) {
					//create parallel threads and call RequestVoteRPC
					for (String info : sockets) {
						String[] temp = info.split(" ");
						ReqVoteThread th = new ReqVoteThread(temp[0], temp[1]);
						threadPool.submit(th);
					}
				}
				else{
					myStatus = Status.LEADER;
					System.out.println("Becoming leader due to too few members.");
					electionTimer.cancel();
				}
				timeout = rand.nextInt(151) + 150;
				electionTimer = new Timer();
				electionTimer.schedule(new TimeoutTaskForElection(), timeout);
			}
			else electionTimer.cancel();  // if is leader, cancel the clock
		}
	}

	class TimeoutTaskForRevote extends TimerTask
	{
		private String targetSocket;
		private String targetName;
		private Timer timer;
		public TimeoutTaskForRevote(Timer timer,String socket,String serverName)
		{
			this.timer = timer;
			this.targetSocket = socket;
			this.targetName = serverName;
		}
		public void run()
		{
			sendRequest(targetSocket,targetName,timer);
		}
	}



	private void sendRequest(String targetSocket,String targetName,Timer aTimer)
	{
		try {
			aTimer.cancel();
			aTimer = new Timer();
			resendTimerMap.put(targetName,aTimer);  // update key value
			XmlRpcClient client = new XmlRpcClient(targetSocket);
			Vector params = new Vector();
			params.add(mySocket);
			params.add((Integer) currentTerm);
			params.add((Integer) lastIndex);
			params.add((Integer) lastTerm);
			aTimer.schedule(new TimeoutTaskForRevote(aTimer, targetSocket,targetName), REQ_TIMEOUT);

			System.out.println("Try sending RequestVote to "+targetName);

			Vector results = (Vector) client.execute(targetName+".RequestVote", params);

			// if get return value then cancel the schedule event
			aTimer.cancel();
			int returnTerm = (int) results.get(0);
			boolean returnFlag = (boolean) results.get(1);
			if (returnFlag) {
				System.out.println("Get vote from "+targetName);
				countLock.lock();
				voteCount++;
				countLock.unlock();
				if(voteCount>=(nodeNumber/2)+1)
				{
					myStatus = Status.LEADER;
					electionTimer.cancel();  //cancel the election event
					// When becomes a leader what should do next?
				}
			}
			else System.out.println("Fail to get vote from "+targetName);

			if(currentTerm<returnTerm)  // see if should update the term
				currentTerm = returnTerm;

		}catch (Exception e)
		{
			e.printStackTrace();
		}

	}


	public Vector RequestVote(String socket,int term,int lastLogIndex, int lastLogTerm)
	{
		Vector results = new Vector();
		results.add((Integer)this.currentTerm);
		boolean flag = isMoreUpToDate(term,lastLogIndex,lastLogTerm);
		boolean shouldVote =false;

		if(myStatus != Status.FOLLOWER)  //see if should turn back to follower
		{
			if (flag && (term > currentTerm)) { // find more up-to-date server
				electionTimer.cancel();       // stop electionTimer immediately
				myStatus = Status.FOLLOWER;
				voteLock.lock();
				voteFor = null;
				voteLock.unlock();
			}
			else shouldVote = false;
		}
		if(myStatus == Status.FOLLOWER) { // if the server itself is a follower, and accept vote,then stay follower
			electionTimer.cancel();
			electionTimer = new Timer();
			electionTimer.schedule(new TimeoutTaskForElection(), timeout);  //restart
			if(flag) {
				voteLock.lock();
				if (voteFor == null) {
					voteFor = socket;
					shouldVote = true;
				} else shouldVote = false;
				voteLock.unlock();
			}
			else shouldVote =false;
		}

		if(currentTerm < term)
			currentTerm = term;
		results.add((Boolean)shouldVote);
		return results;
	}

	private boolean isMoreUpToDate(int receive_term,int receive_last_index,int receive_last_term)   // check whether the
	{
		if(receive_term<currentTerm) return false;
		if(receive_last_term<lastTerm) return  false;
		if(receive_last_index < lastIndex) return false;
		return true;
	}

	// ----------------------- vote , appendEntry and leader thread--------------------------//

	class ReqVoteThread extends Thread
	{
		private String targetSocket;
		private String targetName;   //server name
		public ReqVoteThread(String targetSocket,String targetName)
		{
			this.targetName = targetName;
			this.targetSocket = targetSocket;
		}
		public void run()
		{
			Timer voteTimer = resendTimerMap.get(targetName);
			sendRequest(targetSocket,targetName,voteTimer);
		}
	}
	class AppendEntryThread extends Thread
	{

	}
	class LeaderThread extends Thread
	{
		public void run()
		{
			while (myStatus == Status.LEADER)
			{

			}
		}
	}

	/*------------------------------End of Server Communication Module -----------------------------------*/






	/*======================================================== Provided Server Functions ======================================================================*/

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

	/*====================================================== Test Server Methods ========================================================*/

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
			BufferedReader cmdReader = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Enter the configuration file path: ");
			String configpath = cmdReader.readLine();
			File configFile = new File(configpath);
			while(!configFile.exists())
			{
				System.out.println("Error!config file not found in: "+ configpath +"\nEnter the path again:");
				configpath = cmdReader.readLine();
				configFile = new File(configpath);
			}
			cmdReader.close();
			BufferedReader reader = new BufferedReader(new FileReader(configpath));
			String line =reader.readLine();
			String []info = line.split(" ");
			String port = info[0];
			String serverName = info[1];
			reader.close();
			WebServer server = new WebServer(Integer.parseInt(port));
			server.addHandler(serverName, new Server(configpath));
			server.start();


			System.out.println("Started successfully.");
			System.out.println("Accepting requests. (Halt program to stop.)");

			//? why cannot use server.join?
			//server.join();

		} catch (Exception exception){
			System.err.println("Server: " + exception);
		}
	}
}
