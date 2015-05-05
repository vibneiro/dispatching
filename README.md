# Dispatch
**Dispatch** is an alternative java concurrency library that provides specialized dispatchers for parallelization of work on multicore architectures.


**Author**: Ivan Voroshilin

**email**: vibneiro@gmail.com

**blog**: ivoroshilin.com

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

1. Unbalanced tasks cause not efficient CPU-utilization. The goal is to use CPU-cores more efficiently.
2. Tasks are not blocked by I/O and reasonably small to be proccessed. This come in handy, especially for event-driven async processing. 

Details:

For tasks that differ in execution time, some dispatch queues might be more active than others causing unfair balance among workers (threads). ForkJoinPool is used under the hood for this reason. The work is spread out more effeciently unlike in the standard implementations of Executors by the virtue of work-stealing.

The idea it to separate the queue from the worker, FIFO semantics are retained for tasks with the same dispatchId. 
[ConcurrentLinkedHashMap](https://code.google.com/p/concurrentlinkedhashmap/) is used for LRU-caching of taskIds.

Possible drawbacks in scalability:
 - With the increase of CPUs there might be a higher contention inside ConcurrentLinkedHashMap. I cannot tell definetely, however, this requires benchmarking.

There are 2 versions of this dispatcher:
 - JDK 7 or earlier: based on Guava's *ListenableFuture*.
 - JDK 8: based on *CompletableFuture*.

###[ThreadBoundDispatcher.java](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-8/src/main/java/vibneiro/dispatchers/ThreadBoundHashDispatcher.java)

When to use:

1. Each tasksId is stricty pinned to a particular Thread by its hash. Each thread has a separate queue
2. Tasks might be blocked by I/O.


Description:

TODO

###TODO exponential back-off logic
