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

import org.apache.cassandra.utils.FBUtilities;

/**
 * This class contains configurations that are specific to each cloud system and
 * the deployment environment.
 */
public class Config {
  // The root directory of CoFI.
  static final String ROOT_DIRECTORY = "/cofi";
  // The phase file.
  static final String STAGE_FILENAME = ROOT_DIRECTORY + "/stage.txt";
  // The interesting variable file.
  static final String INTERESTING_VARS_FILENAME = ROOT_DIRECTORY + "/interesting-variables.txt";
  // The directory for profiling logs.
  static final String PROFILING_LOG_DIRECTORY = ROOT_DIRECTORY + "/profiling-logs";

  // The address of the yCozy engine.
  static final String FI_ENGINE_IP = "127.0.0.1";
  static final int FI_ENGINE_PORT = 31569;

  static final int fanoutFactor = 3;
  static final int zoomDepth = 3;

  /**
   * The ID of the current node. The ID should be in the form "nd#####". A
   * possible value for ##### is the hashcode of the current node's IP address.
   * @return The ID of the current node.
   */
  static String currentNodeID() {
    if (nodeID == null) {
      logger.error("Node ID hasn't been set.", new IllegalStateException());
      Runtime.getRuntime().halt(1);
    }
    return nodeID;
  }

  // Please change the constructor to use the logger for your system.
  public static class Logger {
    private org.slf4j.Logger logger;

    Logger(String ownerName) {
      logger = org.slf4j.LoggerFactory.getLogger(ownerName);
    }

    public void debug(String msg) { logger.debug(msg); }
    public void debug(String msg, Throwable t) { logger.debug(msg, t); }
    public void info(String msg) { logger.info(msg); }
    public void info(String msg, Throwable t) { logger.info(msg, t); }
    public void warn(String msg) { logger.warn(msg); }
    public void warn(String msg, Throwable t) { logger.warn(msg, t); }
    public void error(String msg) { logger.error(msg); }
    public void error(String msg, Throwable t) { logger.error(msg, t); }
  }

  /***********************************************
   * The following fields can be left unchanged. *
   ***********************************************/

  public static String nodeID = IDUtils.getHashedNodeID(FBUtilities.getBroadcastAddress().toString());
  private static Logger logger = new Logger("COFI_CONFIG");
}
