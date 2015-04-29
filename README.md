# Dispatch
Dispatch is an alternative java concurrency library that provides specialized dispatchers for parallelization of work on multicore architectures. 


**Author**: Ivan Voroshilin

**email**: vibneiro@gmail.com

**blog**: ivoroshilin.com

## Types of dispatchers

###BalancingDispatcher:

Requirement: 

1. Unbalanced tasks cause unfair CPU-utilization. The goal is to use CPU-cores more efficiently.
2. Multiple tasks with the same Id should be processed sequentially.

Description:

For tasks that differ in execution time, some dispatch queues might be more active than others causing unfair balance among workers (threads). The work in this dispatcher is spread out more evenly unlike in standard implementations.  By separating the queue from the worker, FIFO semantics are retained.

There are 2 versions of this dispatcher:
 - JDK 7 or earlier: based on top of Guava's *ListenableFuture*.
 - JDK 8: based on top of *CompletableFuture*. TBD

###HashDispatcher

Requirement: 

TODO

Description:

TODO
