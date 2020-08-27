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
package cofi.faultinjection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import cofi.invariant.Invariant;
import cofi.util.Config;
import cofi.util.Logger;

public class EventManager {
  // The invariant that we are using.
  private static Invariant invariant = null;

  /**********************
   * Per-run variables. *
   **********************/
  // All the related events in the current run. Buffering them for the analysis
  // at the end of the run.
  // TODO: We don't need to remember all the events now. Remove it.
  private static ArrayList<Event> events = new ArrayList<>();
  // An up-to-date snapshot of the current global state.
  private static HashMap<String, String> curState = new HashMap<>();
  // A trace of the global state after each related events. This is used to
  // detect recovery messages.
  private static ArrayList<HashMap<String, String>> states = new ArrayList<>();
  // The send types failed in the current run.
  public static HashSet<SendType> failedSendTypes = new HashSet<>();

  /**************************
   * Variables across-runs. *
   **************************/
  // Is the current run the first test run?
  private static boolean firstRun = true;
  // Did the previous run fail any message?
  static boolean hasFailedMsgs = false;
  // The number of times that the engine has used the current failure scenario.
  private static int triedTimesOfCurScenario = 1;
  // The current failure scenario.
  private static HashSet<SendType> curFailureScenario = new HashSet<>();
  // All the send event types that have ever existed in the previous runs.
  private static HashSet<SendType> allSendTypes = new HashSet<>();
  // The current failure plan.
  private static FailurePlan curFailurePlan;

  // The status of the simulated network partition
  enum PartitionStatus { PENDING, STARTED, ENDED }
  private static PartitionStatus partitionStatus = PartitionStatus.PENDING;

  // The current fault injection policy.
  public static String policy = "Practical";

  /************************************************
   * Initialize the variables for a new test run. *
   ************************************************/

  static void init(Invariant inv) {
    invariant = inv;
  }

  public static void refresh() {
    events = new ArrayList<>();
    curState = new HashMap<>();
    states = new ArrayList<>();
    failedSendTypes.clear();
    partitionStatus = PartitionStatus.PENDING;
  }

  /**
   * Construct a failure scenario for the current test run. A failure scenario
   * specifies which messages to fail. In the first run, the failure scenario
   * will fail no message. In the latter runs, failure scenarios will be
   * constructed based on the following rules:
   * 1. If the previous run has revealed any new message to fail, this run will
   *    add any new message to the failure scenario.
   * 2. Otherwise, if the previous run hasn't failed any messages, this run will
   *    retry the same failure scenario. The retry will continue until the
   *    configured number of times.
   * 3. Otherwise, construct a new failure scenario based on the next failure
   *    plan.
   * @return Whether there are still new scenarios to try.
   */
  static boolean createNewPartitionScenario() {
    // Don't fail any message during the first test run.
    if (firstRun) {
      Logger.debug("The first run.");
      firstRun = false;
      return true;
    }

    // Before the 2nd run, we need to initialize the current failure plan. If we
    // cannot find a failure plan to start with, return false.
    if (curFailurePlan == null) {
      curFailurePlan = FailurePlan.nextFailurePlan();
      if (curFailurePlan == null) {
        Logger.debug("Don't have any failure plan.");
        return false;
      }
    }

    // Case 1: Expand the current scenario if the previous run has revealed new
    // messages to fail.
    HashSet<SendType> newScenario =
            curFailurePlan.makeFailureScenario(allSendTypes);
    if (!newScenario.equals(curFailureScenario)) {
      curFailureScenario = newScenario;
      triedTimesOfCurScenario = 1;
      hasFailedMsgs = false;
      Logger.debug("Constructed a new failure scenario with the same plan.");
      return true;
    }

    // Case 2: Retry if the previous run didn't fail any message.
    if (!hasFailedMsgs
            && triedTimesOfCurScenario < Config.MAX_RETRIES_PER_SCENARIO) {
      triedTimesOfCurScenario++;
      Logger.debug("Haven't failed a message in the last run. " +
              "Retry for the " + triedTimesOfCurScenario + " time.");
      return true;
    }

    // Case 3: Construct a new failure scenario with the next failure plan.
    curFailurePlan = FailurePlan.nextFailurePlan();
    if (curFailurePlan == null) {
      return false;
    } else {
      curFailureScenario = curFailurePlan.makeFailureScenario(allSendTypes);
      triedTimesOfCurScenario = 1;
      hasFailedMsgs = false;
      Logger.debug("Construct a failure scenario with a new plan.");
      return true;
    }
  }

  /**
   * Load failure plan from a given file.
   * The failure plan consists of several messages to fail.
   * The messages start from the 4th line in the file.
   * The messages all follow the same format:
   * Node <sender-name> sends a message to node <receiver-name> via call stack <stack-hash> at state
   * {<var1>=<val1>, <var2>=<val2>} which leads to state {<var1>=<val1>, <var2>=<val2>}
   */
  static void loadPartitionScenarioFromFile(String fileName) throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
      // Skip the first three lines.
      for (int i = 0; i < 3; ++i) {
        reader.readLine();
      }

      // Load the failed messages from the given failure plan file.
      // We have loaded all the events to fail if we reach an empty line.
      // The remaining contents are for manual verification.
      String msgDescription;
      while ((msgDescription = reader.readLine()) != null
              && msgDescription.length() != 0) {
        Logger.debug("Parsing line: " + msgDescription);
        SendType type = SendType.parse(msgDescription.trim());
        curFailureScenario.add(type);
      }
    }
  }

  /**********************************
   * Managing the current test run. *
   **********************************/

  /**
   * Construct an event object based on given string, and then handle the
   * constructed event.
   * @param eventStr The string representation of an event.
   * @return Whether the yCozy client should proceed with the event. Currently,
   *         this is always true for UpdateEvent and HandleEvent. For SendEvent,
   *         a true is returned when the client can send the pending message.
   */
  static boolean parseAndHandleEvent(String eventStr) {
    // Construct the event.
    Event event = Event.parse(eventStr, events.size());

    // If this event is not interesting, just let the client continue.
    if (!isInterestingEvent(event)) {
      return true;
    }

    // Otherwise, handle the event based on its type.
    boolean shouldProceed = true;
    if (event instanceof SendEvent) {
      shouldProceed = handleSendEvent((SendEvent) event);
    } else if (event instanceof UpdateEvent) {
      shouldProceed = handleUpdateEvent((UpdateEvent) event);
    } else if (event instanceof HandleEvent) {
      shouldProceed = handleHandleEvent((HandleEvent) event);
    }

    // If we are not going to proceed with the event, let the client know.
    // Otherwise, record the new event and the latest global state.
    if (!shouldProceed) {
      return false;
    } else {
      events.add(event);
      states.add(new HashMap<>(curState));
    }

    // Let the client proceed.
    return true;
  }

  /**
   * Check if the given event is interesting.
   * A send event is interesting if its sender or receiver is related to the
   * invariant.
   * An update event is interesting if it happens on a node that is related to
   * the invariant.
   * A handle event is interesting if it happens on a node that is related to
   * the invariant.
   * @param event The given event to test.
   * @return Whether the event is interesting.
   */
  private static boolean isInterestingEvent(Event event) {
    if (event instanceof SendEvent) {
      return invariant.involvesNode(((SendEvent) event).sender)
              || invariant.involvesNode(((SendEvent) event).receiver);
    } else if (event instanceof UpdateEvent) {
      return invariant.involvesNode(event.nid);
    } else if (event instanceof HandleEvent) {
      return invariant.involvesNode(event.nid);
    }
    return false;
  }

  /**
   * Handle the given send event. If the type of the send event is in the
   * current failure scenario, fail the message by returning false. Otherwise,
   * let it pass, and record the type of this send event.
   * @param sendEvent The send event to handle.
   * @return Whether the message can be sent.
   */
  private static boolean handleSendEvent(SendEvent sendEvent) {
    // Get the type of the send event.
    SendType curType = new SendType();
    curType.sender = sendEvent.sender;
    curType.receiver = sendEvent.receiver;
    curType.stackHash = sendEvent.stackHash;
    curType.startState = new HashMap<>(curState);

    // During replay, we just fail the messages in the current failure scenario.
    if (Engine.isReplaying()) {
      if (curFailureScenario.contains(curType)) {
        // Start the network partition if we haven't done so.
        if (partitionStatus == PartitionStatus.PENDING) {
          partitionStatus = PartitionStatus.STARTED;
        }
        // While the network partition is in effect, fail the selected messages.
        return partitionStatus != PartitionStatus.STARTED;
      }
      return true;
    } else if (shouldFailSendType(curType)) {
      // Check if we should fail this type of send event.
      failedSendTypes.add(curType);
      hasFailedMsgs = true;
      return false;
    } else {
      allSendTypes.add(curType);
      return true;
    }
  }

  /**
   * Handle the given update event. Update the current state with the updated
   * variable. Also check if the network partition status will be affected by
   * the new state.
   * @param updateEvent A given update event that updates a variable.
   * @return true.
   */
  private static boolean handleUpdateEvent(UpdateEvent updateEvent) {
    // Update the state.
    String newName = updateEvent.varName;
    String newValue = updateEvent.varValue;
    HashMap<String, String> newState = updateState(curState, newName, newValue);
    if (!newState.equals(curState)) {
      String stateString = "New state: " + newState;
      Logger.debug(stateString);
      Engine.getTestRunner().scriptOutput.append(stateString).append("\n");
    }
    curState = newState;

    // Update the network partition status.
    updatePartitionStatus();

    return true;
  }

  /**
   * Handle the given handle event. Update the current state with the variable
   * at the end of this handle event. Also check if the network partition status
   * will be affected by the new state.
   * @param handleEvent A given handle event that updates a variable.
   * @return true.
   */
  private static boolean handleHandleEvent(HandleEvent handleEvent) {
    // Update the state.
    HashMap<String, String> handlerState = handleEvent.newState;
    HashMap<String, String> newState = new HashMap<>(curState);
    for (String varName : handlerState.keySet()) {
      newState = updateState(newState, varName, handlerState.get(varName));
    }
    if (!newState.equals(curState)) {
      Logger.debug("New state: " + newState);
    }
    curState = newState;

    // Update the network partition status.
    updatePartitionStatus();

    return true;
  }

  /**
   * Update the network partition status based on the current state.
   */
  private static void updatePartitionStatus() {
    if (Engine.isReplaying()) {
      // If the system becomes back to consistent, stop the network partition.
      if (partitionStatus == PartitionStatus.STARTED
              && invariant.holdsAt(curState)) {
        partitionStatus = PartitionStatus.ENDED;
      }
      return;
    }
    // In the 1st run, we don't have a failure plan, so it's no need to update
    // the status of the network partition.
    if (curFailurePlan == null) {
      return;
    }

    if (partitionStatus == PartitionStatus.PENDING
            && curFailurePlan.wantsToKeepState(curState)) {
      partitionStatus = PartitionStatus.STARTED;
      Logger.debug("Partition starts.");
    } else if (partitionStatus == PartitionStatus.STARTED
            && invariant.holdsAt(curState)) {
      partitionStatus = PartitionStatus.ENDED;
      Logger.debug("Partition ends.");
    }
  }

  /**
   * Create a new state by updating the given state with the given variable.
   * @param oldState A given state to update.
   * @param newName The name of the variable to change.
   * @param newVal The value of the variable to change.
   * @return The updated state.
   */
  private static HashMap<String, String> updateState(
          HashMap<String, String> oldState, String newName, String newVal) {
    HashMap<String, String> newState = new HashMap<>(oldState);

    // Remove the updated variable (and its old value) from the state.
    for (String oldName : oldState.keySet()) {
      if (oldName.startsWith(newName) || newName.startsWith(oldName)) {
        newState.remove(oldName);
        break;
      }
    }

    // Add back the new value.
    newState.put(newName, newVal);

    return newState;
  }

  /**
   * Check if we should fail the pending send event based on its type. If the
   * network partition hasn't started or has already finished, we shouldn't fail
   * any send event. Otherwise, if the send type is included in the current
   * failure plan, fail it. For the "Practical" plan, if the failed node is
   * going to send or receive a message that is not specified by the failure
   * scenario, let the message pass and end the partition.
   * If
   * the given send type is in the current failure scenario,
   * @param pendingType The type of the pending send event.
   * @return Whether we should fail the pending send event.
   */
  static boolean shouldFailSendType(SendType pendingType) {
    // If the network partition hasn't started or has already finished, we
    // shouldn't fail any send event.
    if (partitionStatus != PartitionStatus.STARTED) {
      Logger.debug(".");
      return false;
    }

    // Otherwise, only fail the send event if its type is in the failure
    // scenario.
    if (curFailureScenario.contains(pendingType)) {
      Logger.debug("x Failing send: " + pendingType);
      return true;
    }

    // For the "Practical" policy, if the pending type is not in the failure
    // scenario but is related to the failing node, end the network partition.
    if (policy.equals("Practical")) {
      if (pendingType.msgSentOrReceivedBy(curFailurePlan.getNodeToFail())) {
        partitionStatus = PartitionStatus.ENDED;
        Logger.debug("Partition ends.");
      }
    }

    Logger.debug(". Passing send: " + pendingType);

    return false;
  }

  /**********************************
   * Analyze the just finished run. *
   **********************************/

  static void recordNewInconsistentStates() {
    for (HashMap<String, String> state : states) {
      if (!invariant.holdsAt(state)) {
        for (String nodeID : invariant.getNodes()) {
          FailurePlan.addNewFailurePlan(state, nodeID);
        }
      }
    }
  }

  /**********************
   * Debugging methods. *
   **********************/

  static void dumpCurFailureScenario() {
    Logger.debug("Failing the following types of send events:");
    for (SendType t : curFailureScenario) {
      Logger.debug("  " + t);
    }
  }
}
