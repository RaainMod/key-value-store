import java.util.Vector;

public class LogEntry {
    private int index;
    private int term;
    private Vector<String> cmd;

    public LogEntry(int index,int term,Vector<String>cmd)
    {
        this.index = index;
        this.term = term;
        this.cmd = cmd;
    }


}
