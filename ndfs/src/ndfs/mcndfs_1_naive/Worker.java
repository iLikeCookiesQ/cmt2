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
		StateInfo inf;
		pink.add(s);
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
					if(inf == null){
						inf = new StateInfo();
						stateInfo.put(t, inf);
					}
					if(!inf.red){
						dfsRed(t);
					}
				}
			}
		}
		// done with children
		if(s.isAccepting()){
			boolean skip = false;
			synchronized(stateInfo){
				// TODO: ask if synchronized locks onto pointer field, or pointer address
				inf = stateInfo.get(s);
				inf.redCount--;
				if(inf.redCount == 0){
					skip = true;
					synchronized(inf){  // free all waiters
						inf.notifyAll();
					}
				}
			}
			if(!skip){
				synchronized(stateInfo){
					inf = stateInfo.get(s);
					synchronized(inf){ // wait until redCount hits 0
						try{
							while(inf.redCount > 0) {
								System.out.println(Thread.currentThread().getName() + " is waiting on redCount = " + inf.redCount);
								inf.wait();
							}
						} catch(InterruptedException e) {}
					}
				}
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
		List<State> list = graph.post(s);
		int childCount = list.size();
		if(childCount != 0){
			State[] children = list.toArray(new State[childCount]);

			//String threadName = Thread.currentThread().getName();
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
						if(inf == null){
							inf = new StateInfo();
							stateInfo.put(currentChld, inf);
						}
						isRed = inf.red;
					}
					if(!isRed) dfsBlue(currentChld);
				}
			}
		}

		if (s.isAccepting()) {
			synchronized(stateInfo){
				StateInfo inf = stateInfo.get(s);
				if(inf == null){
					inf = new StateInfo();
					stateInfo.put(s, inf);
				} 
				inf.redCount++;	
			}
			dfsRed(s);
		} 
		colors.color(s, Color.BLUE);
	}

	private void nndfs(State s) throws InterruptedException {
		dfsBlue(s);

		//signal main thread that last worker has finished
		synchronized(threadInfo.termination){
			threadInfo.finishedCount++;
			if(threadInfo.finishedCount == threadInfo.nWorker){
				threadInfo.terminationResult = false;
				threadInfo.isTerminationSet = true;
				threadInfo.termination.notify();
			}
		}
	}
	@Override
	public void run() {
		try{
			nndfs(graph.getInitialState());
		} catch (InterruptedException e){}
	}

    /*OLDCODE:public boolean getResult() {
        return result;
    }*/
}
