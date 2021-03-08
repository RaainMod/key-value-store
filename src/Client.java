import java.io.*;
import java.util.*;
import org.apache.xmlrpc.*;

public class Client {
	private final String indexFilePath = ".\\index.txt";
	private final String[] cmdList = {"syncfile","syncdirectory","deletefile","deletedirectory","quit"};
	private final String defaultFilInfoDir = ".\\fileInfoDir\\";
	private final int deafultFileSize = 4096;

	//Function:get hashList strings based on file content, separated by a space
	public String GetHashList(File f,final int fileSize)
	{
		String hashValue =new String();
		try
		{
			FileInputStream fis=new FileInputStream(f);
			byte[] buffer =new byte[fileSize];
			int blockCount= 0;
			while(fis.read(buffer)!=-1)
			{
				hashValue += SHA_256.hash(buffer)+ "\n";  // place a blank between hash values
			}
			fis.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return hashValue;
	}

	//
	public String AppendItemToIndexFile(File f,File fInfo)
	{
		String infoPath;
		if(fInfo == null)
		{
			String fileName = f.getName();
			infoPath = this.defaultFilInfoDir+fileName.substring(0,fileName.indexOf("."))+".txt";
		}
		else infoPath = fInfo.getPath();
		try {
			RandomAccessFile rf = new RandomAccessFile(this.indexFilePath, "rw");
			rf.seek(rf.length());
			String item = f.getName() + " " + infoPath + "\n";
			rf.write(item.getBytes());
			rf.close();
		}catch (IOException e)
		{
			e.printStackTrace();
		}
		return infoPath;
	}

	// create infoFile for each local new file that haven't been recorded in index.txt
	public String CreateNewInfoFile(File targetFile,final int fileSize)
	{
		String fname = targetFile.getName();
		File infoDir = new File(defaultFilInfoDir);
		if(!infoDir.exists())
			infoDir.mkdirs();
		String targetFileInfoPath = defaultFilInfoDir + fname.substring(0,fname.indexOf(".")) + ".txt";
		try {
			File newInfoFile = new File(targetFileInfoPath);
			newInfoFile.createNewFile();
			FileOutputStream fos = new FileOutputStream(newInfoFile);
			String versionStr = String.format("%-8s\n","1");
			fos.write((versionStr).getBytes());
			fos.write((GetHashList(targetFile,fileSize)).getBytes());
			fos.close();
		}catch (IOException e)
		{
			e.printStackTrace();
		}
		return targetFileInfoPath;
	}

	public String FindInfoFilePath(String filePath)
	{
		File indexFile = new File(this.indexFilePath);
		if(!indexFile.exists())
			return null;
		String targetInfoPath = null;
		try {
			String targetFileName = new File(filePath).getName();
			BufferedReader reader = new BufferedReader(new FileReader(indexFile));
			String line;
			while ((line = reader.readLine())!=null)
			{
				line.trim();
				if(line.equals(""))
					continue;
				if(line.substring(0,line.indexOf(" ")).equals(targetFileName))
				{
					targetInfoPath = line.substring(line.indexOf(" ")+1);
					break;
				}
			}
			reader.close();
		}catch (IOException e)
		{
			e.printStackTrace();
		}
		return targetInfoPath;
	}

	// Function: update and sync the specified file to cloud
	// returns the path of the infoFile which records the version and hash values of file
	// if version increase flag is set just increase the version by 1
	public String UpdateLocalFile(String path,final int fileSize,boolean versionIncFlag)
	{
		// open index.txt
		File indexFile =new File(this.indexFilePath);
		String targetInfoPath = null ;  // points to the file records the information of the target file
		try {
			if(!indexFile.exists())
				indexFile.createNewFile();

			//open specified file
			File targetFile = new File(path);
			if(!targetFile.exists())
				targetFile.createNewFile();
			String targetFileName = targetFile.getName();

			targetInfoPath = FindInfoFilePath(path);
			if(targetInfoPath != null)  // if there exists local record
			{
				BufferedReader tempreader = new BufferedReader(new FileReader(targetInfoPath));
				int version = Integer.parseInt(tempreader.readLine().trim());
				if(versionIncFlag)
					version++;
				tempreader.close();

				//update file information
				RandomAccessFile rf = new RandomAccessFile(targetInfoPath,"rw");
				String versionStr = String.format("%-8d\n",version);
				rf.write((versionStr).getBytes());  //add a extra blank in case that modify the version number will not overwrite other information
				if(!versionIncFlag){  // if need update hash list info
					String hashList = GetHashList(targetFile,fileSize);
					rf.write(hashList.getBytes());
				}
				rf.close();
			}
			else // indicates it is a new file that has not been recorded
			{
				targetInfoPath = CreateNewInfoFile(targetFile,fileSize);
				AppendItemToIndexFile(targetFile,new File(targetInfoPath)); //append a new item in index.txt
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return targetInfoPath;
	}

	// another implementation which we already know that the given name file and its targetInfoFile exist
	public String UpdateLocalFile(String path,File targetInfoFile,int fileSize, boolean versionIncFlag)
	{
		File indexFile =new File(this.indexFilePath);
		File targetFile = new File(path);
		String targetInfoPath = null ;  // points to the file records the information of the target file
		try {
			if(!indexFile.exists())
				indexFile.createNewFile();
			if(targetInfoFile == null)
				return UpdateLocalFile(path,fileSize,versionIncFlag);
			targetInfoPath = targetInfoFile.getPath();
			BufferedReader tempreader = new BufferedReader(new FileReader(targetInfoFile));
			int version = Integer.parseInt(tempreader.readLine().trim());
			if(versionIncFlag)
				version++;
			tempreader.close();

			//update file information
			RandomAccessFile rf = new RandomAccessFile(targetInfoPath,"rw");
			String versionStr = String.format("%-8d\n",version);
			rf.write((versionStr).getBytes());  //add a extra blank in case that modify the version number will not overwrite other information
			if(!versionIncFlag){  // if need update hash list info
				String hashList = GetHashList(targetFile,fileSize);
				rf.write(hashList.getBytes());
			}
			rf.close();

		}catch (IOException e)
		{
			e.printStackTrace();
		}
		return targetInfoPath;
	}

	//Function:update and sync all files in specific directory to the cloud
	public boolean UpdateLocalDirectory(String path,final int fileSize) {

		//open index.txt, stored in current project directory
		File indexFile = new File(this.indexFilePath);
		try
		{
			if(!indexFile.exists())
				indexFile.createNewFile();
			//scan all the files in base directory and add the file to hashmap
			HashMap<String,File> fileList = new HashMap<>();
			File baseDir = new File(path);
			File[] subFiles =baseDir.listFiles();
			for(File tempf:subFiles)
				if(tempf.isFile())
					fileList.put(tempf.getName(),tempf);



			//open index.txt ,read and check the information
			//add all files which not recorded to a vector and add information at the end
			BufferedReader reader = new BufferedReader(new FileReader(indexFile));
			ArrayList<String> indexContent =new ArrayList<>(); //store content of index.txt
			String line;
			while((line=reader.readLine())!=null)
			{
				String fileName = line.substring(0,line.indexOf(" "));

				// for each file recorded, check if still exists in base directory
				//if contains, check the hash value and update, otherwise just ignore it
				if(fileList.containsKey(fileName))
				{
					String fileInfoPath = line.substring(line.indexOf(" ")+1);
					UpdateLocalFile(fileList.get(fileName).getPath(),new File(fileInfoPath),fileSize,false);
					indexContent.add(line.concat("\n"));
					fileList.remove(fileName);
				}
			}
			reader.close();
			// all key-value pairs left represent files that are not recorded in index.txt
			for(String aFileName:fileList.keySet())
			{
				String infoFilePath = UpdateLocalFile(fileList.get(aFileName).getPath(),fileSize,false);
				String tempInfo = aFileName + " " + infoFilePath+"\n";
				indexContent.add(tempInfo);
			}

			//update index.txt
			FileOutputStream fos = new FileOutputStream(indexFile);
			for(String s:indexContent)
			{
				fos.write(s.getBytes());
			}
			fos.close();
		}
		catch (IOException e)  //actually this will not happen
		{
			e.printStackTrace();
		}
		return true;
	}


	//return infoFile contents(including version and hash values) in a String
	// and return hash values do not contains carriage
	public Vector<String> GetFileInfo(File infoFile)
	{
		Vector<String> info = new Vector<>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(infoFile));
			String line;
			while((line = reader.readLine())!=null)
			{
				info.add(line.trim());
			}
			reader.close();
			return info;

		} catch(IOException e){
			e.printStackTrace();
		}
		return  null;
	}



	// Function: get new edition of file from cloud and update relevant records
	public void UpdateFromCloud(XmlRpcClient client,File f,File fInfo,Vector<String> cloudHashList)
	{
		try {
			// download file
			FileOutputStream fos = new FileOutputStream(f);
			for(int i=1;i<cloudHashList.size();i++)
			{
				String s =(String)cloudHashList.get(i);
				Vector<String> params = new Vector<>();
				params.add(s);
				params.add(f.getName());
				byte[] data = (byte[]) client.execute("surfstore.getblock",params);
				fos.write(data);
			}
			System.out.println("Successfully update "+f.getName() +"!");
			fos.close();

			//	update index.txt if necessary
			if(fInfo == null) {  //create an infoFile create item in index file and open
				fInfo = new File(AppendItemToIndexFile(f, null));
				fInfo.createNewFile();
			}

			fos = new FileOutputStream(fInfo);
			for(String str: cloudHashList){
				fos.write((str+"\n").getBytes());
			}
			fos.close();

		} catch (Exception exception) {
			System.err.println("Client: " + exception);
		}
	}


	// Function :upload new edition file to cloud and update relevant records in cloud and increase local version record
	public void UpdateToCloud(XmlRpcClient client,File f,File fIno,Vector<String> infoList,final int fileSize)
	{
		String fileName = f.getName();
		try {
			UpdateLocalFile(f.getPath(),fIno,fileSize,true); //increase the local version
			Vector params;
			// update fileInfo
			int version = 1+Integer.parseInt(infoList.get(0).trim());
			infoList.set(0,Integer.toString(version));  // increase the version in infoList

			System.out.println("Local file is newer,try to upload "+fileName+ " to cloud...");
			params = new Vector();
			params.add(fileName);
			params.add(version);
			params.add(infoList);
			boolean flag = (boolean)client.execute("surfstore.updatefile",params);
			if(!flag)
			{ // if upload fail
				System.out.println("Upload fail," + fileName +" just got modified by others.If still want to sync, try later.");
				return ;
			}
			// upload blocks
			FileInputStream fis = new FileInputStream(f);
			byte[] buffer = new byte[fileSize];

			while((fis.read(buffer))!= -1)
			{
				params = new Vector();
				params.add(fileName);
				params.add(buffer);
				client.execute("surfstore.putblock",params);
			}
			fis.close();
			System.out.println("Successfully upload to cloud!");
			params = new Vector();
			params.add(fileName);
			params.add(infoList);
			client.execute("surfstore.finishtransfer",params);
			
		}catch (Exception exception) {
			System.err.println("Client: " + exception);
		}
	}
	public boolean syncFile(XmlRpcClient client,String path,final int fileSize)
	{
		try {
			File targetFile = new File(path);
			String targetFileName = targetFile.getName();
			Vector params = new Vector();
			params.add(targetFileName);
			Vector<String> cloudFileInfo = (Vector<String>) client.execute("surfstore.getfileinfomap", params);
			if(!targetFile.exists()) { //file does not exist just do noting
				if(cloudFileInfo.isEmpty()) {
					System.out.println("Error:Neither cloud or local machine has such file.");
					return true;
				}
				// if there is specific file in given path but there exists in cloud, then download
				UpdateFromCloud(client,targetFile,null,cloudFileInfo);
				return true;
			}

			//  else if local file exists:
			String targetInfoPath = UpdateLocalFile(path,fileSize,false);
			if(targetInfoPath == null) //indicate it is a new file and should update local records first
				targetInfoPath =UpdateLocalFile(path,fileSize,false);
			//  check with cloud file information
			Vector<String> localFileInfo = GetFileInfo(new File(targetInfoPath));

			if (!cloudFileInfo.isEmpty()) { // if cloud saves a copy of file
				int localIndex = 0, cloudIndex = 0;
				boolean isEqual = true;
				while (localIndex < localFileInfo.size() && cloudIndex < cloudFileInfo.size() && isEqual) {
					String localStr = localFileInfo.get(localIndex++).trim();
					String cloudStr = cloudFileInfo.get(cloudIndex++).trim();
					if (!localStr.equals(cloudStr))
						isEqual = false;
				}
				if (!isEqual) {
					if (localIndex == 1)  // indicates that the version is different , should download from clouds
						UpdateFromCloud(client, targetFile, new File(targetInfoPath), cloudFileInfo);
					else   //indicates that local file is newer and should upload to cloud
						//increase the version by 1
						UpdateToCloud(client, targetFile, new File(targetInfoPath), localFileInfo, fileSize);
				}
				else if (localFileInfo.size()!=cloudFileInfo.size()) { //indicates that the hash values are different(longer) so need upload to cloud
					UpdateToCloud(client, targetFile, new File(targetInfoPath), localFileInfo, fileSize);
				}
				else  //other wise means file are same do noting
					System.out.println("The file on local is the same as on the cloud.");
			}
			else
				UpdateToCloud(client, targetFile, new File(targetInfoPath), localFileInfo, fileSize);

		}catch (Exception exception) {
			System.err.println("Client: " + exception);
		}
		return true;
	}

	public boolean syncDirectory(XmlRpcClient client,String path,final int fileSize)
	{
		try {
			File targetDir = new File(path);
			if (!targetDir.exists()) {  // if not exist do noting
				System.out.println("Error: No such directory.");
				return false;
			}
			UpdateLocalDirectory(path, fileSize);
			//get all filenames that store on cloud
			Vector<String> nameList = (Vector<String>) client.execute("surfstore.getallfilenames", new Vector());
			File[] fileList = targetDir.listFiles();
			for (File f : fileList) {
				if (f.isFile()) {
					syncFile(client, f.getPath(), fileSize);
					nameList.removeElement(f.getName());  //remove from name list
				}
			}
			for(String s:nameList) // those left on name list are files that should download from cloud
			{
				syncFile(client,path+s,fileSize);
			}
		}catch (Exception e)
		{
			e.printStackTrace();
		}
			return true;
	}
	public boolean CloudWriteFile(XmlRpcClient client,String path)
	{
		try {
			Vector params = new Vector();
			params.add(path);
			boolean flag =  (boolean)client.execute("surfstore.writetodisk",params);
			if(flag)
				System.out.println("Command Executed Successfully!");
			else
				System.out.println("Error: No Such File on Cloud.");
		}catch (Exception e)
		{
			e.printStackTrace();
		}
		return  true;
	}
	public boolean listCloudFiles(XmlRpcClient client)
	{
		try {
			Vector params = new Vector();
			Vector<String> nameList = (Vector<String>)client.execute("surfstore.listfiles", params);
			if(nameList.isEmpty())
				System.out.println("There is no file on cloud.");
			else{
				int kount=1;
				for(String s: nameList)
					System.out.println("File "+kount+s);
			}
		}catch (Exception e)
		{
			e.printStackTrace();
		}
		return true;
	}
	public boolean commadParser(String []cmd,XmlRpcClient client)
	{
		String cmdType= cmd[0];
		switch (cmdType)
		{
			case "syncfile":
				syncFile(client,cmd[1],Integer.parseInt(cmd[2].trim()));
				break;
			case "syncdirectory":
				syncDirectory(client,cmd[1],Integer.parseInt(cmd[2].trim()));
				break;
			case "test":
				test(client);
				break;
			case "writefile":
				CloudWriteFile(client,cmd[1]);
				break;
			case "ls":

			case "quit":
				return false;
			default:System.out.println("Command error: No such commad registered.");
				break;
		}

		return true;
	}

	public void test(XmlRpcClient client) {
		try {
			String s1 = "abc";
			String s2 = "123";
			Vector params = new Vector();
			params.add(s1);
			params.add(s2);
			client.execute("surfstore.testFunction", params);
		} catch (Exception e)
		{
			e.printStackTrace();
		}

	}


	public static void main (String [] args) {
/*
		if (args.length != 1) {   // command example: $./run-client.sh ip:port
	  		System.err.println("Usage: Client host:port");
			System.exit(1);
	  	}
		//1. deal with argument parameters
		String clientSocket=args[0];   //store connect socket

 */


      	try {
			BufferedReader reader =new BufferedReader(new InputStreamReader(System.in));
			String clientSocket = reader.readLine();
			Client currentClient =new Client();

      		//XmlRpcClient client = new XmlRpcClient("http://localhost:8080/RPC2");
			XmlRpcClient client = new XmlRpcClient(clientSocket+"/RPC2");  //why add RPC2?

			boolean flag = true;
			while(flag)
			{
				String[] commandList = reader.readLine().split(" ");
				flag = currentClient.commadParser(commandList,client);
			}
			reader.close();
/*
		 	// Test ping
			Vector params = new Vector();
		 	client.execute("surfstore.ping", params);
		 	System.out.println("Ping() successful");

		 	// Test PutBlock
		 	params = new Vector();     //why new Vector?
		 	byte[] blockData = new byte[fileSize];
		 	params.addElement(blockData);
         	boolean putresult = (boolean) client.execute("surfstore.putblock", params);
			 System.out.println("PutBlock() successful");

			 // Test GetBlock
			 params = new Vector();
			 params.addElement("h0");
			 byte[] blockData2 = (byte[]) client.execute("surfstore.getblock", params);
			 System.out.println("GetBlock() successfully read in " + blockData2.length + " bytes");


 */
		  } catch (Exception exception) {
			 System.err.println("Client: " + exception);
      }


   }
}
