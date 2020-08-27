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

import java.util.HashSet;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements the methods for retrieving different types of IDs.
 */
public class IDUtils {
  // The counter for each type of message.
  static HashMap<String, Integer> msgCounter = new HashMap<>();

  // A mapping from a thread to the message it is handling.
  private static ConcurrentHashMap<Thread, String> handlingMsgs =
          new ConcurrentHashMap<>();

  // The logger for execution logs.
  private static Config.Logger logger = new Config.Logger("YCZ_IDUTILS");

  /**
   * Get a new message ID for the specified message type. Message IDs are in the
   * form of "sender_receiver_msgType_count". Assuming that the messages between
   * two nodes follow FIFO, this ID can be used to associate the sending and the
   * handling of the same message on two nodes.
   * @param sender The sender ID of the message.
   * @param receiver The receiver ID of the message.
   * @param msgType The type of the message that needs an ID.
   * @return The message ID.
   */
  static synchronized String getMsgIDFromType(
          String sender, String receiver, String msgType) {
    msgType = msgType.replace(" ", "_");
    String msgTypeWithSenderReceiver = sender + "_" + receiver + "_" + msgType;
    int count = 0;
    if (msgCounter.containsKey(msgTypeWithSenderReceiver)) {
      count = msgCounter.get(msgTypeWithSenderReceiver);
    }
    count++;
    msgCounter.put(msgTypeWithSenderReceiver, count);
    return msgTypeWithSenderReceiver + "_" + count;
  }

  /**
   * Register that the current thread is handling the given message. If the
   * current thread is still handling another message, this method will call
   * Config.die().
   * @param msgID The ID of the new message.
   */
  static void registerMessage(String msgID) {
    Thread curThread = Thread.currentThread();
    String oldMsg = handlingMsgs.put(curThread, msgID);
    if (oldMsg != null) {
      logger.error("Thread " + curThread + " is handling two messages: "
                      + oldMsg + " and " + msgID,
              new IllegalStateException());
      Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Unregister that the current thread is handling a message. If the current
   * thread is not handling any message, this method will call Config.die().
   * @return The message being handled.
   */
  static String removeMessage() {
    Thread curThread = Thread.currentThread();
    String oldMsg = handlingMsgs.remove(curThread);
    if (oldMsg == null) {
      logger.error("Thread " + curThread + " is not handling any message.",
              new IllegalStateException());
      Runtime.getRuntime().halt(1);
    }
    return oldMsg;
  }

  /**
   * Generate a dump ID.
   * @return The dump ID.
   */
  static String getDumpID() {
    String methodName = "";
    int stackHash = 0;
    for (StackTraceElement frame : new Throwable().getStackTrace()) {
      // Skip the methods introduced by yCozy.
      if (frame.getClassName().startsWith("ycz")
              || frame.getMethodName().startsWith("yLog")) {
        continue;
      }

      // The name of the application method that is dumping the variables.
      if (methodName.equals("")) {
        methodName = frame.getClassName() + "." + frame.getMethodName();
      }

      // Update the hash of the call stack (with only application methods).
      String singleFrame = frame.getClassName() + "." + frame.getMethodName();
      stackHash ^= singleFrame.hashCode();
    }
    return methodName + "_at_s" + stackHash + "_of_" + getCurThreadGUID();
  }

  /**
   * Get a globally unique ID for the current thread. The ID is in the form of
   * "td#####_nd#####.
   * @return The globally unique ID.
   */
  static String getCurThreadGUID() {
    return "td" + Thread.currentThread().getId() + "_" + Config.currentNodeID();
  }

  /**
   * Return the set of access IDs in the given invariant. Currently, this method
   * can handle three types of invariants:
   * 1. var_a == var_b
   * 2. var_a == CONSTANT_C
   * 3. var_a one of { ... }
   * @param invariant The string representation of an invariant.
   * @return The set of access IDs in the given invariant.
   */
  static HashSet<AccessID> getAccessIDsFromInvariant(String invariant) {
    invariant.trim();
    HashSet<AccessID> ids = new HashSet<>();

    // Get the 1st access ID from the invariant. For the invariants we can
    // handle currently, they always start with an access ID.
    int head = 0, tail = invariant.indexOf(' ');
    ids.add(AccessID.parse(invariant.substring(head, tail)));

    // Get the 2nd access ID if the invariant has it. Currently, the only case
    // we have a 2nd access ID is var_a == var_b.
    head = tail + 4;
    if (invariant.length() <= head + 3) {
      return ids;
    }
    if (invariant.substring(head, head + 2).equals("nd")) {
      ids.add(AccessID.parse(invariant.substring(head)));
    }

    return ids;
  }

  /**
   * Get the hashed node ID for the given raw ID. The hashed ID is of the form
   * nd#####.
   * @param rawID The raw ID.
   * @return The hashed ID.
   */
  static String getHashedNodeID(String rawID) {
    return "nd" + rawID.hashCode();
  }

}
