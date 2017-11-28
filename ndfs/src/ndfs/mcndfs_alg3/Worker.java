package ndfs.mcndfs_alg3;
import graph.State;
//import java.lang.Thread;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.*;
import java.util.HashMap;
import java.util.HashSet;
//import java.lang.Condition;

import graph.Graph;
import graph.GraphFactory;
//import NNDFS.MonitorObject;
//import NNDFS.ThreadInfo;

/**
 * This is a straightforward implementation of Figure 1 of
 * <a href="http://www.cs.vu.nl/~tcs/cm/ndfs/laarman.pdf"> "the Laarman
 * paper"</a>.
 */
public class Worker Extends Thread {
    private static HashMap<State, StateInfo> stateInfo;
    private HashSet<State> pink;
    private final Graph graph;
    private final Colors colors = new Colors();
    public ThreadInfo threadInfo;

    // Throwing an exception is a convenient way to cut off the search in case a
    // cycle is found.
    private static class CycleFoundException extends Exception {
    }

    /**
     * Constructs a Worker object using the specified Promela file.
     *
     *            the Promela file.
     * @throws FileNotFoundException
     *             is thrown in case the file could not be read.
     */
    public Worker(ThreadInfo threaddInfo, HashMap<State, StateInfo> x) throws FileNotFoundException {
	threadInfo = threaddInfo;
	graph = GraphFactory.createGraph(threaddInfo.pFile);
	stateInfo = x;
	pink = new HashSet<State>();
    }

    private void dfsRed(State s) throws InterruptedException {
	if(Thread.interrupted()){
		throw new InterruptedException();
	}
	pink.add(s); // make this node pink colors.color(t, Color.RED);
        for (State t : graph.post(s)) {
            if (colors.hasColor(t, Color.CYAN)) {
                // signal main thread of cycle found
		threadInfo.terminationResult = true;
		synchronized(threadInfo.termination){
			threadInfo.termination.notify();
		}
		return;
                
            } else if (!pink.contains(t)) { 
		synchronized(stateInfo){
                	if(!stateInfo.get(t).red){
				dfsRed(t);
			}
		}
            }
        }
	// done with children
	if(s.isAccepting()){
		synchronized(stateInfo){
			// TODO: ask if synchronized locks onto pointer field, or pointer address
			StateInfo inf = stateInfo.get(s);
			if(--inf.redCount == 0){
				synchronized(inf){  // free all waiters
					inf.notifyAll();
				}
			}
		}
		synchronized(inf){ // wait until redCount hits 0
			inf.wait();
		}
	}
	// shared red true
	synchronized(stateInfo){
		stateInfo.get(s).red = true;
	}
	// pink false
	pink.remove(s);
    }

    private void dfsBlue(State s) throws InterruptedException {
	if(Thread.interrupted()){
		throw new InterruptedException();
	}
        colors.color(s, Color.CYAN);

	// randomly choose a child to begin recursive calls.
	// access remainder of children in order.
	int childCount = graph.post(s).size();
	State[] children = new State[childCount];
	int firstChildIdx = ThreadLocalRandom.current().nextInt(childCount);
	for(int i = 0; i < childCount; i++){
		int currentIdx = (firstChildIdx + i)%childCount; 
		State currentChld = children[currentIdx];
		if(colors.hasColor(currentChld, Color.WHITE)){
			synchronized(stateInfo){
				StateInfo inf = stateInfo.get(currentChld);
				if(inf != null){
					if(!inf.red) dfsBlue(currentChld);
				} else {
					stateInfo.put(currentChld, new StateInfo());
					dfsBlue(currentChld);
				}
			}
		}
	}
	
        if (s.isAccepting()) {
	    synchronized(stateInfo){
	    	StateInfo inf = stateInfo.get(s);
		if(inf != null){ 
			inf.redCount++;
	    	} else {
			stateInfo.put(s, new StateInfo(false, 1));
	    	}
	    }
            dfsRed(s);
            colors.color(s, Color.RED);
        } else {
            colors.color(s, Color.BLUE);
        }
    }

    private void nndfs(State s) throws InterruptedException {
        dfsBlue(s);
	
	//signal main thread that last worker has finished
	if(getAndIncrement(threadInfo.finishedCount) == threadInfo.nWorker -1){
		threadInfo.terminationResult = false;
		synchronized(threadInfo.termination){
			threadInfo.termination.notify();
		}
	}
    }
    @Override
    public void run() {
        nndfs(graph.getInitialState());
    }



    // returns random index 0 < x < nChildren
    int getRandomIndex(int nChildren){
	
    }
    /*OLDCODE:public boolean getResult() {
        return result;
    }*/
}
