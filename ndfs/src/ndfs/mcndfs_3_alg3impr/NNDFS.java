package ndfs.mcndfs_3_alg3impr;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
	public volatile boolean isTerminationSet;
	public File pFile;
	public int nWorker;
	public volatile boolean terminationResult;
	public MonitorObject termination;
	public boolean[] sense;
	public  AtomicInteger finishedCount;
	public ReentrantLock hashMapLock;
	public  AtomicInteger improvisedThreadId;
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
    private Worker[] workers;
    private Thread[] threads;
    public ThreadInfo threadInfo;
  
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
	threadInfo = new ThreadInfo();
	threadInfo.isTerminationSet = false;
	threadInfo.pFile = promelaFile;
	threadInfo.nWorker = nrWorker;
	threadInfo.terminationResult = false;
	threadInfo.termination = new MonitorObject();
	//threadInfo.sense = new boolean[nrWorker]; // TODO: initialize these
	threadInfo.finishedCount = new AtomicInteger(0);
	threadInfo.hashMapLock = new ReentrantLock();
	threadInfo.improvisedThreadId = new AtomicInteger(0);
	stateInfo = new HashMap<State, StateInfo>();

        workers = new Worker[nrWorker];
	threads = new Thread[nrWorker];

        for(int i=0; i<nrWorker; i++){
	    workers[i] = new Worker(threadInfo, stateInfo);
            threads[i] = new Thread(workers[i]);
        }
    }

    @Override
    public boolean ndfs(){
	for(int i = 0; i < threadInfo.nWorker; i++){
	    threads[i].start();
	}

	synchronized(threadInfo.termination){
		try{
			while(!threadInfo.isTerminationSet){
				threadInfo.termination.wait();
			}
		} catch (InterruptedException e) {}
	}
	if(threadInfo.terminationResult){
	//if(true){
	    //TODO terminate children
	    for(int i = 0; i < threadInfo.nWorker; i++){
		threads[i].interrupt();
	    }
	}
	return threadInfo.terminationResult;
    }
}
