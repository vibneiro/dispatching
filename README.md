# Dispatch
**Dispatch** is an alternative highly-concurrenct library for java, providing a set of dispatchers for parallelization of work on multicore-CPU architectures.

The main idea was born from high-frequency trading where low latency is a priority, smoothly moving into scalable concurrent processing, ending up with an advanced caching and a number of dispatchers.

This work is a part of my research, which shows different trade-offs proved by performance benchmarks.

**Author**: Ivan Voroshilin

**email**: vibneiro@gmail.com

**blog**: ivoroshilin.com


##How to build:
1. git clone https://github.com/vibneiro/dispatching.git
2. cd dispatching
2. mvn clean package
3. Jar-files are under:
  - \dispatching\dispatch-java-7\target\dispatch-7.1.0-SNAPSHOT.jar
  - \dispatching\dispatch-java-8\target\dispatch-8.1.0-SNAPSHOT.jar
4. See [Test examples](https://github.com/vibneiro/dispatching/tree/master/dispatch-java-8/src/test/java/vibneiro/dispatchers) to get started.

##Dispatchers

###[Dispatcher.java](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-8/src/main/java/vibneiro/dispatchers/Dispatcher.java)
The main interface implemented by all dispatchers.

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

Prunning of the map happens only for entries having completed futures and is done on reaching cache capacity (atomically) via WeakReference values. tryLock is used for optimistic cache eviction, the idea is derived from Guava/Caffeine projects.

There are 2 versions of this dispatcher:
 - [JDK 7](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-7/src/main/java/vibneiro/dispatchers/WorkStealingDispatcher.java) and later: based on Guava's *ListenableFuture*.
 - [JDK 8](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-8/src/main/java/vibneiro/dispatchers/WorkStealingDispatcher.java) and later: based on *CompletableFuture*.

###[ThreadBoundDispatcher.java](https://github.com/vibneiro/dispatching/blob/master/dispatch-java-8/src/main/java/vibneiro/dispatchers/ThreadBoundHashDispatcher.java)

When to use:

1. Each tasksId must be  stricty pinned to a particular Thread. 
2. Tasks mustn't differ much in the computation size.

Details:
Each tasksId is stricty pinned to its Thread. Each thread has a separate BlockingQueue and processes tasks in the FIFO order.

## Benchmarks

Benchmarks were written on JMH framework and run on iMac i5 CPU @ 2.50GHz (4 core) 18 GB Yosemite.
For testing I used the following params:
 - bounded, unbounded caches;
 - 2 executors (ThreadPoolExecutor, ForkJoinPool);
 - 32 user threads
 - 3 test-cases: 1) a single queue 2) 1 time-queue (1 task per queue). 3) randomly filled multiple queues

[java 7 Benchmarks](https://github.com/vibneiro/dispatching/tree/master/benchmarks-java-7)

[java 8 Benchmarks](https://github.com/vibneiro/dispatching/tree/master/benchmarks-java-8)

How to run (java 8, for java 7 replace with "7 where appropriate below):

1. git clone https://github.com/vibneiro/dispatching.git
2. cd dispatching
3. mvn clean package
4. cd benchmarks-java-8

5.
 - CaffeinedDispatcherBenchmark: 
java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8-1.0-SNAPSHOT.jar CaffeinedDispatcherBenchmark -p cacheType="Bounded, Unbounded" -wi 5 -i 5
 - WorkStealingDispatcherBenchmark:
 java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8-1.0-SNAPSHOT.jar WorkStealingDispatcherBenchmark -p cacheType="Unbounded" -p threadPoolType="ForkJoinPool,FixedThreadPool" -wi 5 -i 5

- ThreadBoundHashDispatcher:
java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8-1.0-SNAPSHOT.jar ThreadBoundHashDispatcherBenchmark -wi 10 -i 5

Results:

####Bounded Caching:

![Random dispatchIds from a fixed set](https://cloud.githubusercontent.com/assets/3040823/8034389/e25c08fc-0def-11e5-84dd-b95140376a46.png)

![Single dispatchId](https://cloud.githubusercontent.com/assets/3040823/8034425/31a448b6-0df0-11e5-8517-e3c6e0eb2976.png)

![Unique dispatchids](https://cloud.githubusercontent.com/assets/3040823/8034434/48da9170-0df0-11e5-80d8-bfba759e75d7.png)

####Unbounded Caching:
![Random dispatchIds from a fixed set](https://cloud.githubusercontent.com/assets/3040823/8034902/f0904b68-0df4-11e5-9980-8be66eb471ea.png)

![Single dispatchId](https://cloud.githubusercontent.com/assets/3040823/8034903/f2284c50-0df4-11e5-8932-f9ea9d084de0.png)

![Unique dispatchids](https://cloud.githubusercontent.com/assets/3040823/8034892/e4e1d7be-0df4-11e5-9684-970f1f2fd706.png)
