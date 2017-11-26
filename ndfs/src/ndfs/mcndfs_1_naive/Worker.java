package ndfs.mcndfs_1_naive;
import graph.State;
import java.lang.Thread;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.*;
import java.util.HashMap;
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
//TODO: data structure for pink? no longer use the local red.
public class Worker extends Thread {
    private static HashMap<State, StateInfo> stateInfo;
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
    public Worker(ThreadInfo threaddInfo) throws FileNotFoundException {
	threadInfo = threaddInfo;
	graph = GraphFactory.createGraph(threaddInfo.pFile);
	stateInfo = new HashMap<State, StateInfo>();
    }

    private void dfsRed(State s) throws InterruptedException {
	if(Thread.interrupted()){
		throw new InterruptedException();
	}
	colors.color(s, Color.RED); // make this node pink colors.color(t, Color.RED);
        for (State t : graph.post(s)) {
            if (colors.hasColor(t, Color.CYAN)) {
                // signal main thread of cycle found
		threadInfo.terminationResult = true;
		synchronized(threadInfo.termination){
			threadInfo.termination.notify();
		}
		return;
                
            } else if (!colors.hasColor(t, Color.RED)) { // this red is the pink from the paper
                if(!stateInfo.get(t).red){ // this is the actual red
			dfsRed(t);
		}
            }
        }
	if(s.isAccepting()){
		StateInfo inf = stateInfo.get(s);
		if(--inf.redCount == 0){
			synchronized(inf){  // free all waiters
				inf.notifyAll();
			}
		}
		synchronized(inf){ // wait until redCount hits 0
			inf.wait();
		}
	}
	// TODO:actual pink false
	// TODO:shared red true

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
			StateInfo inf = stateInfo.get(currentChld);
			if(inf != null){
				if(!inf.red) dfsBlue(currentChld);
			} else {
				stateInfo.put(currentChld, new StateInfo());
				dfsBlue(currentChld);
			}
		}
	}
	
        if (s.isAccepting()) {
	    StateInfo inf = stateInfo.get(s);
	    if(inf != null){
		inf.redCount++;
	    } else {
		stateInfo.put(s, new StateInfo(false, 1));
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