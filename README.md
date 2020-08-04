# CoFI: Consistency-Guided Fault Injection for Cloud Systems

**CoFI** (**Co**nsistency-guided **F**ault **I**njection, pronounced as "coffee") 
is a tool for injecting network partitions when testing cloud systems.

We observe that, network partitions can leave cloud systems in inconsistent
states, where *partition bugs* (bugs triggered by network partitions) are more 
likely to occur. Based on this observation, CoFI first infers invariants
(i.e., consistent states) among different nodes in a cloud system. 
Once observing a violation to the inferred invariants (i.e., inconsistent 
states) while running the cloud system, CoFI injects network partitions to
prevent the cloud system from recovering back to consistent states, and 
thoroughly tests whether the cloud system still proceeds correctly at
inconsistent states. 

## Bugs Detected by CoFI

We applied CoFI to five versions of three widely-used cloud systems including
Cassandra-3.11.5, HDFS-3.3.0, HDFS-2.10.0, YARN-3.3.0, and YARN-2.10.0. The 
following table shows the bugs detected by CoFI in these systems.

Bug ID | Failure Symptom
-------|-----------------
[CASSANDRA-15758](https://issues.apache.org/jira/browse/CASSANDRA-15758) | Thread crashes
[CASSANDRA-15548](https://issues.apache.org/jira/browse/CASSANDRA-15548) | A created keyspace can't be found
[CASSANDRA-15546](https://issues.apache.org/jira/browse/CASSANDRA-15546) | Data read failure
[CASSANDRA-15437](https://issues.apache.org/jira/browse/CASSANDRA-15437) | Decommission failure
[CASSANDRA-11804](https://issues.apache.org/jira/browse/CASSANDRA-11804) | Data access failure
[HDFS-15367](https://issues.apache.org/jira/browse/HDFS-15367) | File metadata inaccessible
[HDFS-15235](https://issues.apache.org/jira/browse/HDFS-15235) | NameNode crashes
[YARN-10301](https://issues.apache.org/jira/browse/YARN-10301) | Fail to stop a YARN service
[YARN-10294](https://issues.apache.org/jira/browse/YARN-10294) | Misleading error message
[YARN-10288](https://issues.apache.org/jira/browse/YARN-10288) | Invalid application state transition
[YARN-10232](https://issues.apache.org/jira/browse/YARN-10232) | Invalid application state transition
[YARN-10231](https://issues.apache.org/jira/browse/YARN-10231) | Misleading error message

## Getting Started

*Tutorials coming soon!*

## Publication

If you are interested in how network partitions affect cloud systems and 
how CoFI effectively and efficiently detects partition bugs,
you can find more details in our paper listed below.
If you use our tool, please cite our paper.

**CoFI: Consistency-Guided Fault Injection for Cloud Systems**<br/>
[Haicheng Chen](http://web.cse.ohio-state.edu/~chen.4800/),
[Wensheng Dou](http://www.tcse.cn/~wsdou/),
[Dong Wang](http://www.tcse.cn/~wangdong18/),
[Feng Qin](http://web.cse.ohio-state.edu/~qin)<br/>
35th IEEE/ACM International Conference on Automated Software Engineering (ASE 2020)