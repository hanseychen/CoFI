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

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.*;

public class Client {
  // The current stage of yCozy.
  private enum Stage { MINING, INJECTION }
  private static Stage stage;
  // The access paths to track during the PROFILING stage.
  private static ArrayList<AccessPath> accessPaths = new ArrayList<>();
  // The access IDs to track during the TESTING stage.
  private static ArrayList<AccessID> accessIDs = new ArrayList<>();

  // The logger for execution logs.
  private static Config.Logger logger = new Config.Logger("COFI_CLIENT");

  /******************
   * Initialization *
   ******************/

  /**
   * Load access paths. If CoFI is running at the invariant mining stage, load
   * the access paths from the user-specified interesting variables. If CoFI is
   * running at the fault injection stage, load the access paths from the
   * current invariant. Making this a static initialization block so that CoFI
   * is initialized whenever it is used, without the need of finding a proper
   * call site.
   */
  static {
    // Load the current stage.
    try (BufferedReader reader = new BufferedReader(new FileReader(
            Config.STAGE_FILENAME))) {
      stage = Stage.valueOf(reader.readLine().trim());
    } catch (Throwable t) {
      logger.error("Fail to load the stage from file " + Config.STAGE_FILENAME, t);
      Runtime.getRuntime().halt(1);
    }

    logger.info("Running CoFI in the " + stage + " stage.");
    switch (stage) {
      case MINING:
        loadAccessPathsFromInterestingVariables();
        break;
      case INJECTION:
        loadAccessPathsFromCurrentInvariant();
        break;
      default:
        logger.error("Unsupported stage: " + stage.name() + ". Abort.");
        Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Load access paths from the "access-paths.txt" file.
   */
  private static void loadAccessPathsFromInterestingVariables() {
    try (BufferedReader reader = new BufferedReader(new FileReader(
            Config.INTERESTING_VARS_FILENAME))) {
      String newLine;
      while ((newLine = reader.readLine()) != null) {
        newLine = newLine.trim();
        InterestingVariable newVar = new InterestingVariable(newLine);
        accessPaths.add(newVar.getAccessPath());
      }
      logger.info("Loaded " + accessPaths.size() + " access paths.");
    } catch (Throwable t) {
      logger.error("Failed to load interesting variables from "
              + Config.INTERESTING_VARS_FILENAME, t);
      Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Load access IDs from file "invariant-to-test.txt".
   */
  private static void loadAccessPathsFromCurrentInvariant() {
    String invFile = Config.ROOT_DIRECTORY + "/invariant-to-test.txt";
    try (BufferedReader reader = new BufferedReader(new FileReader(invFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        for (AccessID id : IDUtils.getAccessIDsFromInvariant(line)) {
          // Each node should only care about the access IDs for itself.
          if (id.nodeID.equals(Config.currentNodeID())) {
            accessIDs.add(id);
          }
        }
      }
    } catch(Throwable t) {
      logger.error("Fail to load invariants from file " + invFile, t);
      Runtime.getRuntime().halt(1);
    }
  }

  /********
   * APIs *
   ********/

  /**
   * Before sending a message:
   * In the PROFILING run, dump all the variables specified by the access paths
   * to profiling logs.
   * In the TESTING run, send the values of the invariant variables to the yCozy
   * engine, and returns whether the message can be sent.
   * @param sender The sender of the message.
   * @param receiver The receiver of the message.
   * @param msgType The type of the message.
   * @return Whether the message can be sent.
   */
  public static boolean beforeSend(
          String sender, String receiver, String msgType) {
    String msgID = IDUtils.getMsgIDFromType(sender, receiver, msgType);
    logger.debug("About to send message " + msgID);

    switch (stage) {
      case MINING:
        // Get all the variables according to the access paths.
        HashSet<Variable> vars = new HashSet<>();
        getVariablesAndValues(vars);
        // Write the variables to the profiling log.
        String dumpID = IDUtils.getDumpID();
        if (!ProfilingLog.curThreadHasDumps()) {
          ProfilingLog.write1stDump(vars, dumpID);
        }
        ProfilingLog.writeVarsForEvent(vars, "send", msgID, dumpID);
        return true;
      case INJECTION:
        getAndReportInvariantVariables();
        return Messenger.reportSendEvent(sender, receiver, msgID, msgType);
      default:
        return true;
    }
  }

  /**
   * After handling a message:
   * Unregister that the current thread is handling a message.
   * In the PROFILING run, dump all the variables specified by the access paths
   * to the profiling logs.
   * In the TESTING run, send the values of the invariant variables to the yCozy
   * engine, and let the engine know that a message is fully handled.
   * @param sender The sender of the message.
   * @param receiver The receiver of the message.
   * @param msgType The type of the message.
   */
  public static void afterHandle(
          String sender, String receiver, String msgType) {
    String msgID = IDUtils.getMsgIDFromType(sender, receiver, msgType);
    logger.debug("Finish handling message " + msgID);
    if (stage == Stage.MINING) {
      // Get all the variables according to the access paths.
      HashSet<Variable> vars = new HashSet<>();
      getVariablesAndValues(vars);
      // Make sure the current thread has an initial dump.
      String dumpID = IDUtils.getDumpID();
      if (!ProfilingLog.curThreadHasDumps()) {
        ProfilingLog.write1stDump(vars, dumpID);
      }
      ProfilingLog.writeVarsForEvent(vars, "receive", msgID, dumpID);
    } else if (stage == Stage.INJECTION) {
      getAndReportInvariantVariables();
      Messenger.reportHandleEvent(sender, receiver, msgID);
    }
  }

  /***************************************
   * Getting Variables and Their Values. *
   ***************************************/

  /**
   * Get the value of all the variables identified by the access paths.
   * @param vars The retrieved variables.
   */
  private static void getVariablesAndValues(HashSet<Variable> vars) {
    for (AccessPath path : accessPaths) {
      String rootClassName = path.getRootClass();
      ArrayList<String> accessors = path.getAccessors();
      String accessor = accessors.get(0);

      // Get the static field object for this access path.
      Object staticField = null;
      try {
        Class rootClass = Class.forName(rootClassName);
        Method getter = rootClass.getMethod("yGet_" + accessor);
        staticField = getter.invoke(null);
      } catch (Throwable t) {
        logger.info("Fail to get static variable: " +
                rootClassName + "." + accessor,
                t);
        Runtime.getRuntime().halt(1);
      }

      // Zoom in according to the access path.
      String curPath = rootClassName + "." + accessor;
      Variable.zoomInAndGetVars(staticField, accessors, 0, curPath, vars);
      logger.debug("Got " + vars.size() + " interesting variables.");
    }
  }

  /**
   * Get the latest variable values based on the access IDs, and report these
   * variable values to the yCozy engine.
   */
  private static void getAndReportInvariantVariables() {
    // Get the latest variable values.
    HashMap<String, String> variablesAndValues = new HashMap<>();
    for (AccessID id : accessIDs) {
      variablesAndValues.putAll(id.getNameAndValue());
    }

    // Update the local state.
    Variable.updateVariables(variablesAndValues);

    // Report the latest variables to the yCozy engine.
    for (String varName : variablesAndValues.keySet()) {
      Messenger.reportVariable(varName, variablesAndValues.get(varName));
    }
  }
}
