# Dispatch
**Dispatch** is an alternative java concurrency library that provides specialized dispatchers for parallelization of work on multicore architectures.


**Author**: Ivan Voroshilin

**email**: vibneiro@gmail.com

**blog**: ivoroshilin.com


##Quick start
1. git clone https://github.com/vibneiro/dispatching.git
2. cd dispatching
2. mvn clean package
3. Jar-files are under:
  - \dispatching\dispatch-java-7\target\dispatch-7.1.0-SNAPSHOT.jar
  - \dispatching\dispatch-java-8\target\dispatch-8.1.0-SNAPSHOT.jar
4. See [Test examples](https://github.com/vibneiro/dispatching/tree/master/dispatch-java-8/src/test/java/vibneiro/dispatchers) to get started.

##Description

###[Dispatcher.java](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-8/src/main/java/vibneiro/dispatchers/Dispatcher.java)
Main interface implemented by all dispatchers.

Each task has a corresponding dispatchId. Tasks with the same dispatchId get processed sequentially (syncrhonously).
This allows to run:
 - Not thread-safe tasks
 - Dependent tasks
 - Time-ordered tasks

## Types of dispatchers

###[WorkStealingDispatcher.java](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-8/src/main/java/vibneiro/dispatchers/WorkStealingDispatcher.java)

When to use: 

1. Unbalanced tasks cause inefficient CPU-utilization. The goal is to use CPU-cores more efficiently.
2. Tasks are not blocked by I/O and reasonably small to be proccessed. This come in handy, especially for event-driven async processing. 

Details:

For tasks that differ in execution time, some dispatch queues might be more active than others causing unfair balance among workers (threads). ForkJoinPool is used under the hood for this reason by default. The work is spread out more effeciently unlike in the standard implementations of Executors by the virtue of work-stealing. However, you can pass in your ExecutorService.

The main idea in this dispatcher it to separate the queue from the worker, FIFO semantics are retained for tasks with the same dispatchId. 

Prunning of the map happens only for entries having completed futures and is done on reaching cache capacity (atomically) via WeakReference values. tryLock is used for optimistic cache eviction, the idea is inherited from Guava/Caffeine projects.

There are 2 versions of this dispatcher:
 - [JDK 7](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-7/src/main/java/vibneiro/dispatchers/WorkStealingDispatcher.java) and later: based on Guava's *ListenableFuture*.
 - [JDK 8](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-8/src/main/java/vibneiro/dispatchers/WorkStealingDispatcher.java) and later: based on *CompletableFuture*.

###[ThreadBoundDispatcher.java](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-8/src/main/java/vibneiro/dispatchers/ThreadBoundHashDispatcher.java)

When to use:

1. Each tasksId must be  stricty pinned to a particular Thread. 
2. Tasks mustn't differ much in the computation size.

Details:
Each tasksId is stricty pinned to its Thread. Each thread has a separate BlockingQueue and processes tasks in the FIFO order.

###TODO [PriorityQueuedDispatcher.java]

###TODO Exponential back-off logic

## TODO Benchmarks

## TODO Experimental

