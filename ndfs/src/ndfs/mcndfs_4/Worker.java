package ndfs.mcndfs_4;

import java.util.concurrent.locks.ReentrantLock;
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
 * This is a straightforward implementation of Algorithm 3 of
 * <a href="http://www.cs.vu.nl/~tcs/cm/ndfs/laarman.pdf"> "the Laarman
 * paper"</a>.
 */
public class Worker implements Runnable {
	static final boolean DEBUG = false;
	String threadName; // used for debugging
	int threadNo; // this is sometimes used to make workers explore diffent children of a node first in a nonrandom way
	public ConcurrentHashMap<State, StateInfo> stateInfo; // the hashmap storing all per state information 
	private final Graph graph;
	private final Colors colors = new Colors();
	public ThreadInfo threadInfo; // 

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
	public Worker(ThreadInfo threaddInfo, ConcurrentHashMap<State, StateInfo> x) throws FileNotFoundException {
		threadInfo = threaddInfo;
		graph = GraphFactory.createGraph(threaddInfo.pFile);
		stateInfo = x;
		threadNo = threadInfo.improvisedThreadId.getAndIncrement();
		//pink = new HashSet<State>();
	}

	private void dfsRed(State s) throws InterruptedException {
		if(Thread.interrupted()){
			throw new InterruptedException();
		}
		StateInfo inf;
		colors.color(s, Color.PINK);
		List<State> list = graph.post(s);
		int childCount = list.size();
		if(childCount != 0){
			State[] children = list.toArray(new State[childCount]);
			/*if(!stateInfo.containsKey(s)){
				inf = new StateInfo();
				stateInfo.put(s, inf);
			}*/
			//threadInfo.hashMapLock.lock();
				int firstChildIdx = stateInfo.get(s).permutationRed.getAndIncrement();
			//threadInfo.hashMapLock.unlock();
			boolean isRed;
			for (int i = 0; i < childCount; i++) {
				int currentIdx = (firstChildIdx + i)%childCount;
				State currentChld = children[currentIdx];
				if (colors.hasColor(currentChld, Color.CYAN)) {
					// signal main thread of cycle found
					synchronized(threadInfo.termination){
						threadInfo.terminationResult = true;
						threadInfo.isTerminationSet = true;
						threadInfo.termination.notify();
					}
					return;
		
				} else if (!colors.hasColor(currentChld, Color.PINK)) {
					if(!stateInfo.get(currentChld).red){
						dfsRed(currentChld);
					}
				}
			}
		}
		// done with children
		// decrement redCount and await it becoming 0 in the following block
		//int localCount;
		if(s.isAccepting()){
			//threadInfo.hashMapLock.lock();
				inf = stateInfo.get(s);
				synchronized(inf){
					if(stateInfo.get(s).redCount.decrementAndGet() == 0){// free all waiters
						//if(DEBUG) System.out.println(threadName + " at State "
							//+ s.toString() + "has notified all.");
						inf.notifyAll();
					}
				}
			//threadInfo.hashMapLock.unlock();
			synchronized(inf){
				while(inf.redCount.get() != 0){
					if(DEBUG) System.out.println(threadName + " at State "
						+ s.toString() + " is waiting on redCount = " + inf.redCount);
					inf.wait();
					if(DEBUG) System.out.println(threadName + " at State "
						+ s.toString() + " has been freed.");
				}	
			}
		}
		// shared red true
		//threadInfo.hashMapLock.lock();
			stateInfo.get(s).red = true;
		//threadInfo.hashMapLock.unlock();
		// pink false
		// pink.remove(s);
	}

	private void dfsBlue(State s) throws InterruptedException {
		if(Thread.interrupted()){
			throw new InterruptedException();
		}
		StateInfo inf; // auxiliary variable to reduce garbage collection overhead
		boolean allRed = true;
		colors.color(s, Color.CYAN);

		// randomly choose a child to begin recursive calls.
		// access remainder of children in order.
		List<State> list = graph.post(s);
		int childCount = list.size();
		if(childCount != 0){
			int firstChildIdx; // used to send threads in a different direction in the graph
			State[] children = list.toArray(new State[childCount]);

			// if the current node isn't in the hashmap, set firstChldIdx to threadNo.
			// this saves some locking.
			if(!stateInfo.containsKey(s)){
				firstChildIdx = threadNo;
			} else {
				//threadInfo.hashMapLock.lock();
					firstChildIdx = stateInfo.get(s).permutationBlue.getAndIncrement();
				//threadInfo.hashMapLock.unlock();
			}
			boolean isRed;
			for(int i = 0; i < childCount; i++){
				int currentIdx = (firstChildIdx + i)%childCount;
				State currentChld = children[currentIdx];
				// early cycle detection
				if(colors.hasColor(currentChld, Color.CYAN)){
					if(s.isAccepting() || currentChld.isAccepting()){
						// signal main thread of cycle found
						synchronized(threadInfo.termination){
							threadInfo.terminationResult = true;
							threadInfo.isTerminationSet = true;
							threadInfo.termination.notify();
						}
						return;
					}
				} // end early cycle detection
				if(colors.hasColor(currentChld, Color.WHITE)){
					//threadInfo.hashMapLock.lock();
						if(!stateInfo.containsKey(currentChld)){ // lock the hashmap in case key isn't present
							threadInfo.hashMapLock.lock();
								if(!stateInfo.containsKey(currentChld)){
									stateInfo.put(currentChld, new StateInfo());
								}
							threadInfo.hashMapLock.unlock();
						}
					//threadInfo.hashMapLock.unlock();
					if(!stateInfo.get(currentChld).red){
						dfsBlue(currentChld);
					}
				}
				//threadInfo.hashMapLock.lock(); the following 4 lines are commented because allRed checks are moved outside of this loop
					//inf = stateInfo.get(currentChld);
					//if(!stateInfo.get(currentChld).red) allRed = false;
				//threadInfo.hashMapLock.unlock();
			} // end for all children

			// improvement start: move allred checks outside of the for all children loop so that you check
			// more recent information when looking up redness.
			for(int i = 0; i < childCount; i++){
				if(!stateInfo.get(children[i]).red) allRed = false;
			} // end improvement
		} // end if(childCount != 0)
		
		//if(DEBUG) System.out.println("Thread " + threadNo + " has dealt with the children of node " + s.toString());
		if(allRed){
			/*threadInfo.hashMapLock.lock();
				inf = stateInfo.get(s);
				if(!stateInfo.containsKey(s)){
					inf = new StateInfo();
				}
				inf.red = true;
				stateInfo.put(s, inf);
			threadInfo.hashMapLock.unlock();*/
			if(!stateInfo.containsKey(s)){ // lock the hashmap in case key isn't present
				threadInfo.hashMapLock.lock();
					if(!stateInfo.containsKey(s)){
						stateInfo.put(s, new StateInfo());
					}
				threadInfo.hashMapLock.unlock();
			} 
			stateInfo.get(s).red = true;
		} else if (s.isAccepting()) {
			/*threadInfo.hashMapLock.lock();
				inf = stateInfo.get(s);
				if(!stateInfo.containsKey(s)){
					inf = new StateInfo();
					stateInfo.put(s, inf);
				} 
				inf.redCount++;	
				stateInfo.put(s, inf);
			threadInfo.hashMapLock.unlock();*/
			if(!stateInfo.containsKey(s)){ // lock the hashmap in case key isn't present
				threadInfo.hashMapLock.lock();
					if(!stateInfo.containsKey(s)){
						stateInfo.put(s, new StateInfo());
					}
				threadInfo.hashMapLock.unlock();
			}
			stateInfo.get(s).redCount.getAndIncrement();
			dfsRed(s);
		} 
		colors.color(s, Color.BLUE);
	}

	private void nndfs(State s) throws InterruptedException {
		dfsBlue(s);

		//signal main thread that last worker has finished
		synchronized(threadInfo.termination){
			int i = threadInfo.finishedCount.incrementAndGet();
			if(DEBUG) System.out.println(threadName + " with threadNo: " + threadNo  + 
				" has finished graph traversal and set finishedCount to " + i);
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
}
