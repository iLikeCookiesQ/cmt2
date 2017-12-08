package ndfs.mcndfs_3_alg3impr;

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
	static final boolean DEBUG = true;
	String threadName;
	int threadNo; // this is used to make workers explore diffent children of a node first in a nonrandom way
	public HashMap<State, StateInfo> stateInfo;
	//private HashSet<State> pink;
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
		threadNo = threadInfo.improvisedThreadId.getAndIncrement();
		//pink = new HashSet<State>();
	}

	private void dfsRed(State s) throws InterruptedException {
		if(Thread.interrupted()){
			throw new InterruptedException();
		}
		StateInfo inf;
		/*if(s.isAccepting()){
			threadInfo.hashMapLock.lock();
				inf = stateInfo.get(s);
				if(!stateInfo.containsKey(s)) inf = new StateInfo();
				inf.redCount++;
				stateInfo.put(s, inf);
			threadInfo.hashMapLock.unlock();
		}*/
		colors.color(s, Color.PINK);
		List<State> list = graph.post(s);
		int childCount = list.size();
		if(childCount != 0){
			State[] children = list.toArray(new State[childCount]);
			//int firstChildIdx = ThreadLocalRandom.current().nextInt(childCount);
			boolean isRed;
			for (int i = 0; i < childCount; i++) {
				int currentIdx = (threadNo + i)%childCount;
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
					threadInfo.hashMapLock.lock();
						inf = stateInfo.get(currentChld);
						/*if(!stateInfo.containsKey(currentChld)){
							if(DEBUG) System.out.println(threadName + 
								" has a red search that found a null hashmap entry on node " + currentChld.toString());
							inf = new StateInfo();
							stateInfo.put(currentChld, inf);
						}*/
						isRed = inf.red;
					threadInfo.hashMapLock.unlock();
					if(!isRed){
						dfsRed(currentChld);
					}
				}
			}
		}
		// done with children
		// decrement redCount and await it becoming 0 in the following block
		//int localCount;
		if(s.isAccepting()){
		//if(true){
			threadInfo.hashMapLock.lock();
				inf = stateInfo.get(s);
				synchronized(inf){
					inf.redCount--;
					stateInfo.put(s, inf);
					if(inf.redCount == 0){// free all waiters
						//if(DEBUG) System.out.println(threadName + " at State "
							//+ s.toString() + "has notified all.");
						inf.notifyAll();
					}
				}
			threadInfo.hashMapLock.unlock();
			/*while(localCount > 0){			
					threadInfo.hashMapLock.lock();
					localCount = stateInfo.get(s).redCount;
					if(localCount > 0){	
						synchronized(inf){
							threadInfo.hashMapLock.unlock();
							if(DEBUG) System.out.println(threadName + " at State "
								+ s.toString() + " is waiting on redCount = " + localCount);
							try{
								inf.wait();
							} catch(InterruptedException e) {}
							if(DEBUG) System.out.println(threadName + " at State "
								+ s.toString() + " has been freed.");	
						}
					}	
				
			}*/
			
			synchronized(inf){
				while(inf.redCount != 0){
					if(DEBUG) System.out.println(threadName + " at State "
						+ s.toString() + " is waiting on redCount = " + inf.redCount);
					inf.wait();
					if(DEBUG) System.out.println(threadName + " at State "
						+ s.toString() + " has been freed.");
				}	
			}
		}
		// shared red true
		threadInfo.hashMapLock.lock();
			inf = stateInfo.get(s);
			inf.red = true;
			stateInfo.put(s, inf);
		threadInfo.hashMapLock.unlock();
		// pink false
		// pink.remove(s);
	}

	private void dfsBlue(State s) throws InterruptedException {
		if(Thread.interrupted()){
			throw new InterruptedException();
		}
		StateInfo inf;
		boolean allRed = true;
		colors.color(s, Color.CYAN);

		// randomly choose a child to begin recursive calls.
		// access remainder of children in order.
		List<State> list = graph.post(s);
		int childCount = list.size();
		if(childCount != 0){
			State[] children = list.toArray(new State[childCount]);

			//System.out.println("Child Count " + childCount + " with thread " + threadName);
			//int firstChildIdx = ThreadLocalRandom.current().nextInt(childCount);
			boolean isRed;
			for(int i = 0; i < childCount; i++){
				int currentIdx = (threadNo + i)%childCount;
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
				}
				// end early cycle detection
				if(colors.hasColor(currentChld, Color.WHITE)){
					threadInfo.hashMapLock.lock();
						inf = stateInfo.get(currentChld);
						if(!stateInfo.containsKey(currentChld)){
							inf = new StateInfo();
							stateInfo.put(currentChld, inf);
						}
						isRed = inf.red;
					threadInfo.hashMapLock.unlock();
					if(!isRed){
						dfsBlue(currentChld);
					}
				}
				threadInfo.hashMapLock.lock();
					inf = stateInfo.get(currentChld);
					if(!inf.red) allRed = false;
				threadInfo.hashMapLock.unlock();
			}
		}
		//if(DEBUG) System.out.println("Thread " + threadNo + " has dealt with the children of node " + s.toString());
		if(allRed){
			threadInfo.hashMapLock.lock();
				inf = stateInfo.get(s);
				if(!stateInfo.containsKey(s)){
					inf = new StateInfo();
				}
				inf.red = true;
				stateInfo.put(s, inf);
			threadInfo.hashMapLock.unlock();
		} else if (s.isAccepting()) {
			threadInfo.hashMapLock.lock();
				inf = stateInfo.get(s);
				if(!stateInfo.containsKey(s)){
					inf = new StateInfo();
					stateInfo.put(s, inf);
				} 
				inf.redCount++;	
				stateInfo.put(s, inf);
			threadInfo.hashMapLock.unlock();
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
