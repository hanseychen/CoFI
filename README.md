# CoFI: Consistency-Guided Fault Injection for Cloud Systems

**CoFI** (**Co**nsistency-guided **F**ault **I**njection, pronounced as "coffee") 
is a tool for injecting network partitions when testing cloud systems.

We observe that, network partitions can leave cloud systems in inconsistent
states, where *partition bugs* (bugs triggered by network partitions) are more 
likely to occur. Based on this observation, CoFI first infers invariants
(i.e., consistent states) among different nodes in a cloud system. 
Once detecting a violation to the inferred invariants (i.e., inconsistent 
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

The easiest way to build and run CoFI is using Docker.
You can directly download the docker images of CoFI and its example systems
and starting testing an example system using CoFI.
Or you can following this tutorials to build the docker images from scratch.

### Building CoFI's Docker Image

Run the following command at the repository's root directory to build CoFI's
docker image.

```Bash
$ make build-image
```

This will create a Docker image called `hanseychen/cofi:0.1`.

### Building Cassandra's Docker Image.

To build the Docker image for Cassandra, run the following command in the
repository's root.

```Bash
$ cd examples/cassandra-3.11.5 && make build-image
```

This will create a Docker image called `cofi:cassandra-3`. This cassandra image
is built on top of the CoFI image. So, we can apply CoFI to Cassandra using the
Cassandra image. When building the Cassandra, CoFI instruments Cassandra's code
to enable accessing the runtime values of possible interesting variables. Next, 
we'll run a Docker container using this image and run CoFI to test Cassandra.

### Starting the Docker Container.

Inside Cassandra's directory, run the following command to start a Docker
container called `ca` using the Cassandra image.

```Bash
$ make run-container image_name=cofi:cassandra-3 container_name=ca
```

### Mining invariants

CoFI runs in two stages: an invariant mining stage and a fault injection stage.
To mine invariants of Cassandra, run the following command under the `/app` 
directory inside the container.

```Bash
$ /cofi/bin/mine-invariants.sh interesting-variables.txt test-case/data-test.sh test-case/cleanup.sh
```

This command will run the `data-test.sh` test case and log the runtime values of
the interesting variables stored in `interesting-variables.txt`. Afterwards, 
CoFI's invariant mining engine will process the logged values and generate
interesting invariants for guiding the later fault injection testing. The
generated invariants are stored in `/cofi/selected-invariants.txt`.

### Testing Cassandra

To run CoFI's fault injection engine, run the following command under the `/app`
directory inside the container.

```Bash
$ /cofi/bin/test-invariants.py test-case/data-test.sh test-case/cleanup.sh
```

This command will iterate over the interesting invariants inside 
`/cofi/selected-invariants.txt`, and use the invariants to guide fault injection
tests on Cassandra.

### Understanding the failure report

If a test failure occurs when running the fault injection tests, a failure
report will be generated under the `/app` directory. The file name follows the
format of `failure-plan-<timestamp>.txt`. Inside the failure plan, you can find
the invariant used to trigger the failure, the messages being failed, and the 
outputs generated by the test case.


## Publication

If you are interested in how network partitions affect cloud systems and 
how CoFI effectively and efficiently detects partition bugs,
you can find more details in our paper listed below.
If you use our tool, please cite our paper.

**CoFI: Consistency-Guided Fault Injection for Cloud Systems**<br/>
[Haicheng Chen](https://hanseychen.github.io),
[Wensheng Dou](http://www.tcse.cn/~wsdou/),
[Dong Wang](http://www.tcse.cn/~wangdong18/),
[Feng Qin](http://web.cse.ohio-state.edu/~qin)<br/>
35th IEEE/ACM International Conference on Automated Software Engineering (ASE 2020)
