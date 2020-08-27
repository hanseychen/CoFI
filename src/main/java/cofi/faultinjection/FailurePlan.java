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

import cofi.util.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class FailurePlan {
  // The inconsistent state this failure plan tries to keep.
  private HashMap<String, String> stateToKeep;
  // The node this failure plan tries to fail.
  private String nodeToFail;

  // All the possible failure plans.
  static private ArrayList<FailurePlan> allFailurePlans = new ArrayList<>();
  // The index of the current failure plan.
  static private int planIndex = -1;

  /**
   * Check whether the given state is the one this failure plan tries to keep.
   * @param state A state to check.
   * @return Whether the state is the one this failure plan tries to keep.
   */
  boolean wantsToKeepState(HashMap<String, String> state) {
    return stateToKeep.equals(state);
  }

  /**
   * Based on the current failure plan, construct a failure scenario using the
   * given set of send event types. The constructed scenario will contain all
   * and only the types that starts at the `stateToKeep` and would be
   * sent/received by the `nodeToFail`.
   * @param allSendTypes The send event types to construct a failure scenario.
   * @return A failure scenario consists of send event types to fail.
   */
  HashSet<SendType> makeFailureScenario(
          HashSet<SendType> allSendTypes) {
    HashSet<SendType> newScenario = new HashSet<>();

    for (SendType t : allSendTypes) {
      if ((t.sender.equals(nodeToFail) || t.receiver.equals(nodeToFail))
              /*&& t.startState.equals(stateToKeep)*/) {
        newScenario.add(t);
      }
    }

    return newScenario;
  }

  /**
   * Get the next failure plan.
   * @return If there is at least one more failure plan, return the next.
   *         Otherwise, return null.
   */
  static FailurePlan nextFailurePlan() {
    planIndex += 1;
    if (planIndex >= allFailurePlans.size()) {
      return null;
    } else {
      FailurePlan newPlan = allFailurePlans.get(planIndex);
      Logger.debug("New failure plan: " + newPlan);
      return newPlan;
    }
  }

  /**
   * Create a new failure plan using the given state and node ID, and add it to
   * the plans if we haven't.
   * @param newState The state to keep.
   * @param newNode The node to fail.
   */
  static void addNewFailurePlan(
          HashMap<String, String> newState, String newNode) {
    FailurePlan newPlan = new FailurePlan();
    newPlan.stateToKeep = new HashMap<>(newState);
    newPlan.nodeToFail = newNode;

    if (!allFailurePlans.contains(newPlan)) {
      allFailurePlans.add(newPlan);
    }
  }

  /**
   * Check if this failure plan is failing the given node.
   * @param nodeID The ID of a node.
   * @return This failure plan is failing the given node.
   */
  boolean isFailingNode(String nodeID) {
    return nodeToFail.equals(nodeID);
  }

  String getNodeToFail() {
    return nodeToFail;
  }

  @Override
  public String toString() {
    return "Keeping the state at " + stateToKeep +
            " by failing " + nodeToFail + ".";
  }

  @Override
  public int hashCode() {
    return nodeToFail.hashCode() ^ stateToKeep.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FailurePlan)) {
      return false;
    }

    FailurePlan another = (FailurePlan) o;

    return nodeToFail.equals(another.nodeToFail)
            && stateToKeep.equals(another.stateToKeep);
  }
}
