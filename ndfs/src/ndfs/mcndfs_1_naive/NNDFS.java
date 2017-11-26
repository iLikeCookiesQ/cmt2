package ndfs.mcndfs_1_naive;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.Thread;
//import java.lang.Condition;
import ndfs.NDFS;

/**
 * Implements the {@link ndfs.NDFS} interface, mostly delegating the work to a
 * worker class.
 */

class MonitorObject{
}

class ThreadInfo{
	public File pFile;
	public int nWorker;
	public boolean terminationResult;
	public MonitorObject termination;
	public boolean[] sense;
	public int finishedCount;
}

public class NNDFS implements NDFS {
    private Thread[] workers;
    public ThreadInfo threadInfo;
    /*
    public volatile boolean[] terminationState = new boolean[1];
    public Condition termination;*/

    /**
     * Constructs an NDFS object using the specified Promela file.
     *
     * @param promelaFile
     *            the Promela file.
     * @throws FileNotFoundException
     *             is thrown in case the file could not be read.
     */
    public NNDFS(File promelaFile, int nrWorker) throws FileNotFoundException {
	threadInfo.pFile = promelaFile;
	threadInfo.nWorker = nrWorker;
	threadInfo.terminationResult = false;
	threadInfo.termination = new MonitorObject();
	threadInfo.sense = new boolean[nrWorker]; // TODO: initialize these
	threadInfo.finishedCount = 0;

        workers = new Thread[nrWorker];
        for(int i=0; i<threadInfo.nWorker; i++){
            workers[i] = new Worker(threadInfo);
        }

        //this.worker = new Worker(promelaFile);
    }

    @Override
    public boolean ndfs() throws InterruptedException{
        for(int i = 0; i < threadInfo.nWorker; i++){
	    // TODO put barrier inside threads to avoid cycles being found
	    // before wait() is called here
            workers[i].start();
        }

	synchronized(threadInfo.termination){
		threadInfo.termination.wait();
	}
	if(threadInfo.terminationResult){
	    //TODO terminate children
	    for(int i = 0; i < threadInfo.nWorker; i++){
		workers[i].interrupt();
	    }
	}
	return threadInfo.terminationResult;
	
        //OLDCODE:return worker.getResult();
    }
}
