/*
 * Copyright 2020 Haicheng Chen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cofi.client;

import java.io.FileWriter;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class manages the logs during the profiling phase.
 */
public class ProfilingLog {
  // The threads that have already dumped some values.
  private static ConcurrentHashMap<Thread, Boolean> threadsWithDump =
          new ConcurrentHashMap<>();
  // The profiling log file of each thread.
  private static ConcurrentHashMap<Thread, FileWriter> varLogFiles =
          new ConcurrentHashMap<>();

  private static Config.Logger logger = new Config.Logger("YCZ_PROFILINGLOG");

  /**
   * Check if the current thread has already written to the profiling log.
   * @return True if the current thread has written to the profiling log.
   */
  static boolean curThreadHasDumps() {
    return threadsWithDump.containsKey(Thread.currentThread());
  }

  /**
   * Create the initial dump entry in the profiling log of the current thread.
   * The log merging algorithm will use the variables in this entry to prune out
   * variables whose value stays the same during the execution.
   * @param vars Variables to dump.
   * @param dumpID The ID of the dump point.
   */
  static void write1stDump(HashSet<Variable> vars, String dumpID) {
    threadsWithDump.put(Thread.currentThread(), true);
    writeVarsForEvent(vars, "init", "noMsg", dumpID);
  }

  /**
   * Write the given variables to the profiling log.
   * @param vars The variables to log.
   * @param action Which action is performed, i.e., init, send, or receive.
   * @param msgId The ID of the message that is acted on.
   * @param dumpID The dump ID associate with these variables.
   */
  static void writeVarsForEvent(
          HashSet<Variable> vars,
          String action,
          String msgId,
          String dumpID) {
    // First, let's record what's going on:
    // Are we sending a message or receiving one? Which message?
    StringBuilder dumpSB = new StringBuilder(action).append("\n");
    dumpSB.append(msgId).append("\n");

    // FIXME: This is no longer needed, please remove it as well we the parser
    //        in the log merging phase.
    dumpSB.append(1).append("\n");

    // Then, let's record the variables: their names, types, and values.
    dumpSB.append(dumpID).append("\n");
    dumpSB.append(vars.size()).append("\n");
    for (Variable v : vars) {
      dumpSB.append(v.accessPath).append("\n");
      dumpSB.append(v.type).append("\n");
      dumpSB.append(v.value).append("\n");
    }
    dumpSB.append("\n");

    // Flush now.
    writeString(dumpSB.toString());
  }

  /**
   * Write a string to the profiling log of the current thread.
   * @param str The string to write.
   */
  private static void writeString(String str) {
    FileWriter file = getVarLogFile();
    try {
      file.write(str);
      file.flush();
    } catch (Throwable t) {
      logger.error("Error when " + IDUtils.getCurThreadGUID() +
              " tries to write to the variable file.", t);
      Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Get the profiling log file of the current thread.
   * @return A writer of the profiling log.
   */
  private static FileWriter getVarLogFile() {
    Thread curThread = Thread.currentThread();
    if (!varLogFiles.containsKey(curThread)) {
      try {
        varLogFiles.put(
                curThread,
                new FileWriter(
                        Config.PROFILING_LOG_DIRECTORY
                                + "/" + IDUtils.getCurThreadGUID() + ".txt"));
      } catch (Throwable t) {
        logger.error("error when "+ IDUtils.getCurThreadGUID() +
                " tries to get the variable file.", t);
        Runtime.getRuntime().halt(1);
      }
    }
    return varLogFiles.get(curThread);
  }
}
