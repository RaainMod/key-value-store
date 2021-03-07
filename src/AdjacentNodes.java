import java.util.Hashtable;
import java.util.Vector;

public class AdjacentNodes {
    private Vector<String> adjNodeSocket;  // store the other server socket in the working cluster
    private Hashtable<String,Integer> nextIDMap;  //map the server socket to the index that should send

    public boolean addNode(String nodeSocket)  // add a new Node
    {
        return true;
    }


    public  boolean appendEntries(LogFile lf,String scoket)
    {
        return  true;
    }
}
