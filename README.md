# Dispatch
**Dispatch** is an alternative highly-concurrenct library for java, providing a set of dispatchers for parallelization of work on multicore-CPU architectures.

**Author**: Ivan Voroshilin

**email**: vibneiro@gmail.com

**blog**: ivoroshilin.com


##How to build:
```{r, engine='Shell', count_lines}
git clone https://github.com/vibneiro/dispatching.git
cd dispatching
mvn clean package
```

Jar-files are under:
  - \dispatching\dispatch-java-7\target\dispatch-7.1.0-SNAPSHOT.jar
  - \dispatching\dispatch-java-8\target\dispatch-8.1.0-SNAPSHOT.jar
See [Test examples](https://github.com/vibneiro/dispatching/tree/master/dispatch-java-8/src/test/java/vibneiro/dispatchers) to get started.

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

## MicroBenchmarks

Benchmarks were written on JMH framework for JDK 7 and 8 separately and run on iMac Core i5 CPU @ 2.50GHz (4 cores) 8 GB, Yosemite OS.
All the benchmark work with an empty Runnable synthetic task to mitigate side-effects.

Benchmark mode: Throughput, ops/time
###TODO: measure with 1 user thread.

3 test-cases: 
   1. A single dispatch-queue: putting new tasks always to the same dispatchId.
   2. Counting dispatchId: one-off queue of size = 1 per task, since dispatchId is always incremented by 1.
   3. Randomly filled set of queues with a size = 32768. TODO: try 1024

The following  params are used for JMH benchmarking:
 - { Bounded, Unbounded } caches; 
   * Purpose: analyze the impact of eviction time on the overall performance.
 - 2 types of ExecutorService { ThreadPoolExecutor, ForkJoinPool };
   * Purpose: analyze the impact of 2 different executors on throughput.
 - 32 user threads for all 3 tests;
   * Purpose: analyze contention impact on concurrent data-structures.

[java 7 Benchmarks](https://github.com/vibneiro/dispatching/tree/master/benchmarks-java-7)

[java 8 Benchmarks](https://github.com/vibneiro/dispatching/tree/master/benchmarks-java-8)

##How to run the benchmark (java 8, for java 7 replace with "7 where appropriate below):
```{r, engine='Shell', count_lines}
git clone https://github.com/vibneiro/dispatching.git
cd dispatching
mvn clean package
cd benchmarks-java-8
```

 - CaffeinedDispatcherBenchmark: 
```{r, engine='Shell', count_lines}
java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8.jar CaffeinedDispatcherBenchmark -p cacheType="Bounded, Unbounded" -wi 5 -i 5
```

 - WorkStealingDispatcherBenchmark:
```{r, engine='Shell', count_lines}
 java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8.jar WorkStealingDispatcherBenchmark -p cacheType="Unbounded" -p threadPoolType="ForkJoinPool,FixedThreadPool" -wi 5 -i 5
```

- ThreadBoundHashDispatcher:
```{r, engine='Shell', count_lines}
java -server -Xms5G -Xmx5G -jar target/benchmarks-java-8.jar ThreadBoundHashDispatcherBenchmark -wi 10 -i 5
```

## Benchmark graphs:

Important note:
As can be seen, after introducing [significant updates](http://openjdk.java.net/jeps/155) to Java 8, ForkJoinPool is a way more scalable, including ConcurrentHashMap changes compared to JDK 7.

####JDK 8:

####Bounded Caching:

![Random dispatchIds from a fixed set](https://cloud.githubusercontent.com/assets/3040823/8034389/e25c08fc-0def-11e5-84dd-b95140376a46.png)

![Single dispatchId](https://cloud.githubusercontent.com/assets/3040823/8034425/31a448b6-0df0-11e5-8517-e3c6e0eb2976.png)

![Unique dispatchids](https://cloud.githubusercontent.com/assets/3040823/8034434/48da9170-0df0-11e5-80d8-bfba759e75d7.png)

####Unbounded Caching:
![Random dispatchIds from a fixed set](https://cloud.githubusercontent.com/assets/3040823/8034902/f0904b68-0df4-11e5-9980-8be66eb471ea.png)

![Single dispatchId](https://cloud.githubusercontent.com/assets/3040823/8034903/f2284c50-0df4-11e5-8932-f9ea9d084de0.png)

![Unique dispatchids](https://cloud.githubusercontent.com/assets/3040823/8034892/e4e1d7be-0df4-11e5-9684-970f1f2fd706.png)

####JDK 7: version jdk1.7.0_71

####Unbounded Caching:

![Random dispatchIds from a fixed set](https://cloud.githubusercontent.com/assets/3040823/8080802/2c58486a-0f78-11e5-9e69-cb505e8df29d.png)

![Single dispatchId](https://cloud.githubusercontent.com/assets/3040823/8081150/2db6f596-0f7b-11e5-8fe8-bd43fff7695a.png)

![Unique dispatchids](https://cloud.githubusercontent.com/assets/3040823/8081173/66826874-0f7b-11e5-9e40-b06fae328b05.png)
