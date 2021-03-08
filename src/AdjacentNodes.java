import java.util.Hashtable;
import java.util.Vector;

public class AdjacentNodes {
    private Vector<String> adjNodeSocket = new Vector<>();  // store the other server name and socket in the working cluster
    private Hashtable<String,Integer> nextIndexMap = new Hashtable<>();  //map the server socket to the index that should send

    public boolean addNode(String socket)  // add a new Node
    {
        adjNodeSocket.add(socket);
        nextIndexMap.put(socket,1);   // send next index of 1 as initial value
        return true;
    }
    public Vector<String>getAllNodeInfo() {return this.adjNodeSocket;}

    public  boolean appendEntries(String scoket)
    {
        return  true;
    }
}
