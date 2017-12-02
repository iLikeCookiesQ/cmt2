package ndfs.mcndfs_1_naive;
import graph.State;
//import java.lang.Thread;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.*;
import java.util.HashMap;
import java.util.HashSet;
//import java.lang.Condition;
import java.util.List;
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
public class Worker implements Runnable {
	static final boolean DEBUG = true;
	String threadName;
	public HashMap<State, StateInfo> stateInfo;
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
		StateInfo inf;
		synchronized(stateInfo){
			inf = stateInfo.get(s);
			if(!stateInfo.containsKey(s)) inf = new StateInfo();
			inf.redCount++;
			stateInfo.put(s, inf);
		}
		pink.add(s);
		boolean isRed;
		for (State t : graph.post(s)) {
			if (colors.hasColor(t, Color.CYAN)) {
				// signal main thread of cycle found
				synchronized(threadInfo.termination){
					threadInfo.terminationResult = true;
					threadInfo.isTerminationSet = true;
					threadInfo.termination.notify();
				}
				return;

			} else if (!pink.contains(t)) {
				synchronized(stateInfo){
					inf = stateInfo.get(t);
					if(!stateInfo.containsKey(t)){
						if(DEBUG) System.out.println(threadName + " has a red search that found a null hashmap entry on node " + t.toString());
						inf = new StateInfo();
						stateInfo.put(t, inf);
					}
					isRed = inf.red;
				}
				if(!isRed){
					dfsRed(t);
				}
			}
		}
		// done with children
		// decrement redCount and await it becoming 0 in the following block
		int localCount;
		if(s.isAccepting()){
			synchronized(stateInfo){
				// TODO: ask if synchronized locks onto pointer field, or pointer address
				inf = stateInfo.get(s);
				localCount = --inf.redCount;
				stateInfo.put(s, inf);
			}
			if(localCount > 0){
				synchronized(inf){
					try{
						while(inf.redCount > 0) {
							if(DEBUG) System.out.println(threadName + 
								" is waiting on redCount = " + inf.redCount);
							inf.wait();
						}
					} catch(InterruptedException e) {}
				}
			} else {
				synchronized(inf){  // free all waiters
					inf.notifyAll();
				}
			} 
		}
		// shared red true
		synchronized(stateInfo){
			inf = stateInfo.get(s);
			inf.red = true;
			stateInfo.put(s, inf);
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
		List<State> list = graph.post(s);
		int childCount = list.size();
		if(childCount != 0){
			State[] children = list.toArray(new State[childCount]);

			//System.out.println("Child Count " + childCount + " with thread " + threadName);
			int firstChildIdx = ThreadLocalRandom.current().nextInt(childCount);
			boolean isRed;
			for(int i = 0; i < childCount; i++){
				int currentIdx = (firstChildIdx + i)%childCount;
				State currentChld = children[currentIdx];
				StateInfo inf;
				if(colors.hasColor(currentChld, Color.WHITE)){
					synchronized(stateInfo){
						inf = stateInfo.get(currentChld);
						if(!stateInfo.containsKey(currentChld)){
							inf = new StateInfo();
							stateInfo.put(currentChld, inf);
						}
						isRed = inf.red;
					}
					if(!isRed){
						dfsBlue(currentChld);
					}
				}
			}
		}
		//if(DEBUG) System.out.println(threadName + " has dealt with the children of node " + s.toString());
		if (s.isAccepting()) {
			/*synchronized(stateInfo){
				StateInfo inf = stateInfo.get(s);
				if(!stateInfo.containsKey(s)){
					inf = new StateInfo();
					//stateInfo.put(s, inf);
				} 
				inf.redCount++;	
				stateInfo.put(s, inf);
			}*/
			dfsRed(s);
		} 
		colors.color(s, Color.BLUE);
	}

	private void nndfs(State s) throws InterruptedException {
		dfsBlue(s);

		//signal main thread that last worker has finished
		synchronized(threadInfo.termination){
			int i = threadInfo.finishedCount.incrementAndGet();
			if(i == threadInfo.nWorker){
				threadInfo.terminationResult = false;
				threadInfo.isTerminationSet = true;
				threadInfo.termination.notify();
			}
		}
	}
	@Override
	public void run() {
		try{
			if(DEBUG) threadName = Thread.currentThread().getName();
			nndfs(graph.getInitialState());
		} catch (InterruptedException e){}
	}

    /*OLDCODE:public boolean getResult() {
        return result;
    }*/
}
