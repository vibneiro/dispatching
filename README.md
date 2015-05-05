# Dispatch
**Dispatch** is an alternative java concurrency library that provides specialized dispatchers for parallelization of work on multicore architectures. 


**Author**: Ivan Voroshilin

**email**: vibneiro@gmail.com

**blog**: ivoroshilin.com

## Types of dispatchers

###WorkStealingDispatcher:

Requirement: 

1. Unbalanced tasks cause unfair CPU-utilization. The goal is to use CPU-cores more efficiently.
2. Multiple tasks with the same Id should be processed sequentially.

Description:

For tasks that differ in execution time, some dispatch queues might be more active than others causing unfair balance among workers (threads). ForkJoinPool is used under the hood for this reason.
[ConcurrentLinkedHashMap](https://code.google.com/p/concurrentlinkedhashmap/) is used for LRU-caching of taskIds.

The work is spread out more effeciently unlike in the standard implementations. The idea it to separate the queue from the worker, FIFO semantics are retained to meet the requirement number 2 above. Hence the queue is not tied to a particular worker-thread. Any free worker can take work from the queue not being aware of which task should be run next. 

There are 2 versions of this dispatcher:
 - JDK 7 or earlier: based on Guava's *ListenableFuture*.
 - JDK 8: based on *CompletableFuture*.

###HashDispatcher

Requirement: 

TODO

Description:

TODO

###TODO exponential back-off logic
