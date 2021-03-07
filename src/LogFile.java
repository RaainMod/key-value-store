import java.util.Vector;

public class LogFile {
    private int lastIndex = 0;
    private int lastTerm = 0;
    private int commitIndex = 0;
    private int lastAppliedIndex = 0;

    public LogFile() {}

    public synchronized int getLastIndex() {return this.lastIndex;}

    public synchronized int getLastTerm() {return this.lastTerm;}

    public synchronized int getCommitIndex() {return this.commitIndex;}

    private synchronized int  getLastAppliedIndex() {return this.lastAppliedIndex;}

    public synchronized boolean appendEntries(Vector entryList)
    {
        return true;
    }

}
