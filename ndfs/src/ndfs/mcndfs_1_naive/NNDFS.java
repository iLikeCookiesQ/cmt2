package ndfs.mcndfs_1_naive;

import graph.State;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.Thread;
import java.util.HashMap;
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
	public AtomicInteger finishedCount;
}

class StateInfo{
	boolean red;
	int redCount;

	StateInfo(){
		red = false;
		redCount = 0;
	}
	StateInfo(boolean b, int i){
		red = b;
		redCount = i;
	}
}

public class NNDFS implements NDFS {
    private Worker work;
    // private Worker[] workers;
    private Thread[] threads;
    public ThreadInfo threadInfo;
    /*
    public volatile boolean[] terminationState = new boolean[1];
    public Condition termination;*/
    public HashMap<State, StateInfo> stateInfo; // to be passed to threads

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
	//threadInfo.sense = new boolean[nrWorker]; // TODO: initialize these
	threadInfo.finishedCount = new AtomicInteger(0);
	stateInfo = new HashMap<State, StateInfo>();

        work = new Worker(threadInfo, stateInfo);
	threads = new Thread[nrWorker];
	/*
	workers = new Worker[nrWorker];
        for(int i=0; i<threadInfo.nWorker; i++){
            workers[i] = new Worker(threadInfo, stateInfo);
        }*/
    }

    @Override
    public boolean ndfs(){
        for(int i = 0; i < threadInfo.nWorker; i++){
	    // TODO put barrier inside threads to avoid cycles being found
	    // before wait() is called here
	    threads[i] = new Thread(work);
            //workers[i].start();
        }

	for(int i = 0; i < threadInfo.nWorker; i++){
	    threads[i].start();
        }

	synchronized(threadInfo.termination){
		try{
			threadInfo.termination.wait();
		} catch (InterruptedException e) {}
	}
	if(threadInfo.terminationResult){
	    //TODO terminate children
	    for(int i = 0; i < threadInfo.nWorker; i++){
		threads[i].interrupt();
	    }
	}
	return threadInfo.terminationResult;
	
        //OLDCODE:return worker.getResult();
    }
}
