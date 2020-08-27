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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/**
 * This class implements the methods for communicating with the yCozy server.
 */
public class Messenger {
  // The logger for execution logs.
  private static Config.Logger logger = new Config.Logger("YCZ_MESSENGER");

  /**
   * Report a variable's name and value to the yCozy engine.
   * @param name The name of the variable.
   * @param value The value of the variable.
   */
  static void reportVariable(String name, String value) {
    reportToYCozyEngine(
            IDUtils.getCurThreadGUID() + " updateVariable " +
                    name + "#####" + value + "#####\n");
  }

  /**
   * Report the send event to the yCozy engine, and return the engine's decision
   * on whether the message can be sent.
   * @param sender The sender's ID.
   * @param receiver The receiver's ID.
   * @param msgID The message's ID.
   * @return The engine's decision on whether the message can be sent.
   */
  static boolean reportSendEvent(String sender, String receiver, String msgID, String msgType) {
    StringBuilder sb = new StringBuilder(IDUtils.getCurThreadGUID())
            .append(" send ")
            .append(IDUtils.getHashedNodeID(sender)).append(" ")
            .append(IDUtils.getHashedNodeID(receiver)).append(" ")
            .append(msgID).append(" ")
            .append(getStackHash() ^ msgType.hashCode()).append("\n");

    boolean pass = reportToYCozyEngine(sb.toString());
    if (!pass) {
      logger.info("The yCozy engine fails message: " + sb, new Throwable());
    }

    return pass;
  }

  /**
   * Get a hash code for the current call stack (excluding yCozy's code).
   * @return The hash code for the current call stack.
   */
  private static int getStackHash() {
    int stackHash = 0;
    Throwable curStack = new Throwable("Current stack");
    for (StackTraceElement frame : curStack.getStackTrace()) {
      if (!frame.getClassName().startsWith("ycz")) {
        stackHash ^= frame.hashCode();
      }
    }
    return stackHash;
  }

  /**
   * Send a string to the yCozy engine.
   * @param content The string content to sent.
   * @return Whether the engine wants us to proceed.
   */
  static boolean reportToYCozyEngine(String content) {
    try (Socket s = new Socket(Config.FI_ENGINE_IP, Config.FI_ENGINE_PORT);
         DataInputStream in = new DataInputStream(s.getInputStream());
         DataOutputStream out = new DataOutputStream(s.getOutputStream())) {
      // TODO: We don't need this preceeding integer any more.
      out.writeInt(2); // Event type: LOG
      out.writeUTF(content);
      return in.readBoolean();
    } catch (Throwable t) {
      logger.error("Failed when talking to the yCozy engine.", t);
      Runtime.getRuntime().halt(1);
    }
    return true;
  }

  /**
   * Report to the yCozy engine that the current node has finished handling the
   * given message.
   * @param sender The sender's ID.
   * @param receiver The receiver's ID.
   * @param msgID The message's ID.
   */
  static void reportHandleEvent(String sender, String receiver, String msgID) {
    // Get the string representation of the checkpoint before and after handling
    // the message.
    String oldCheckpoint = Variable.getLastCheckpointAsString();
    Variable.makeNewCheckpoint();
    String newCheckpoint = Variable.getLastCheckpointAsString();

    StringBuilder sb = new StringBuilder();

    sb.append(IDUtils.getCurThreadGUID()).append(" ");
    sb.append("messageHandling ");
    sb.append(IDUtils.getHashedNodeID(sender)).append(" ");
    sb.append(IDUtils.getHashedNodeID(receiver)).append(" ");
    sb.append(msgID).append(" ");
    sb.append(oldCheckpoint).append(" ");
    sb.append(newCheckpoint).append("\n");

    reportToYCozyEngine(sb.toString());
  }
}
