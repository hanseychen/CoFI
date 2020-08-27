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
package cofi.mining;

import cofi.invariant.Invariant;
import cofi.invariant.VariableEquality;
import cofi.util.Config;
import cofi.variable.InterestingVariable;
import cofi.variable.Variable;
import cofi.util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class InvPruner {
  private static String invFileName = null;
  private static HashSet<InterestingVariable> interestingVariables = new HashSet<>();

  /**
   * Load invariants from the specified invariant file and prune them.
   */
  public static void main(String[] args) {
    init(args);
    try {
      // Load invariants from file.
      HashSet<Invariant> invs = loadInvsFromFile(invFileName);
      Logger.info("Loaded " + invs.size() + " unique invariants.");

      // Derive equality invariants.
      HashSet<Invariant> eqInvs = deriveEqualityInvs(invs);
      Logger.info("There're " + eqInvs.size() + " invariants after deriving " +
              "new equality invariants.");

      // Select invariants with two variables.
      HashSet<VariableEquality> twoVarInvs = getTwoVarInvs(eqInvs);
      Logger.info("There're " + twoVarInvs.size() + " invariants with two" +
              " variables.");

      // Get the invariants that involve two nodes, i.e., global invariants.
      HashSet<VariableEquality> globalInvs = getGlobalInvariants(twoVarInvs);
      Logger.info("There're " + globalInvs.size() + " global invariants.");

      // Only keep invariants on the same metadata.
      HashSet<VariableEquality> sameMetadataInvs = keepSameMetadataInvs(globalInvs);
      Logger.info("There're " + sameMetadataInvs.size() + " invariants on " +
              "the same metadata.");

      writeInvsToFile(sameMetadataInvs, Config.FILE_NAME_SELECTED_INVARIANTS);
    } catch (Throwable t) {
      Logger.fatal("Unexpected exception when ranking invariants.");
      t.printStackTrace(System.err);
      Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Keep invariants that involve variables of the same metadata.
   * @param invariants The set of invariants to analyze.
   * @return The set of invariants that involve variables of the same metadata.
   */
  private static HashSet<VariableEquality>
  keepSameMetadataInvs(HashSet<VariableEquality> invariants) {
    HashSet<VariableEquality> sameMetaInvs = new HashSet<>();
    for (VariableEquality inv : invariants) {
      String metadata1 = null;
      // Get the metadata of the first variable.
      for (InterestingVariable iVar : interestingVariables) {
        if (iVar.getAccessPath().includes(inv.varName1)) {
          metadata1 = iVar.getMetadata();
          break;
        }
      }
      if (metadata1 == null) {
        continue;
      }
      // Get the metadata of the second variable.
      for (InterestingVariable iVar : interestingVariables) {
        if (iVar.getAccessPath().includes(inv.varName2)) {
          if (metadata1.equals(iVar.getMetadata())) {
            sameMetaInvs.add(inv);
          }
        }
      }
    }

    return sameMetaInvs;
  }

  private static void init(String[] args) {
    if (args.length != 2) {
      Logger.error("Incorrect usage.");
      Logger.info("Usage:");
      Logger.info("$ java cofi.InvPruner <invariant> <interesting-vars>");
      System.exit(1);
    }

    invFileName = args[0];

    try (BufferedReader reader = new BufferedReader(new FileReader(args[1]))) {
      String newLine;
      while ((newLine = reader.readLine()) != null) {
        newLine = newLine.trim();
        InterestingVariable newVar = new InterestingVariable(newLine);
        interestingVariables.add(newVar);
      }
    } catch (Throwable t) {
      Logger.error("Failed to load interesting variables from file " + args[1]);
      System.exit(1);
    }
  }

  /**
   * Rank the invariants.
   * @param prioritizedInvs Invariants that should rank higher.
   * @param totalInvs Invariants that should rank lower.
   * @param threshold The number of invariants we want.
   * @return A sorted list of invariants.
   */
  private static ArrayList<Invariant>
  rankInvariants(HashSet<Invariant> prioritizedInvs, HashSet<Invariant> totalInvs, int threshold) {
    ArrayList<Invariant> invs = new ArrayList<>();

    // Get the prioritized invariants first.
    for (Invariant inv : prioritizedInvs) {
      if (invs.size() >= threshold) {
        break;
      } else {
        invs.add(inv);
      }
    }

    // Get the remaining invariants.
    for (Invariant inv : totalInvs) {
      if (invs.size() >= threshold) {
        break;
      } else if (!invs.contains(inv)) {
        invs.add(inv);
      }
    }

    return invs;
  }

  /**
   * Recover the missing equality invariants. For example, if we have two
   * invariants, a == b and b == c, then we can derive a == c.
   * recover
   * @param invs
   * @return
   */
  private static HashSet<Invariant>
  deriveEqualityInvs(HashSet<Invariant> invs) {
    HashSet<Invariant> expandedInvs = new HashSet<>();
    // Get the involved access paths and values.
    HashMap<String, HashSet<String>> id2EquivalentIDs = new HashMap<>();
    for (Invariant inv : invs) {
      for (String id : inv.getVarsAndVals()) {
        if (!id2EquivalentIDs.keySet().contains(id)) {
          id2EquivalentIDs.put(id, new HashSet<>());
        }
      }
    }

    // Keep deriving equivalent IDs.
    boolean foundNewEq = true;
    while (foundNewEq) {
      foundNewEq = false;
      for (Invariant inv : invs) {
        // Since ID1 == ID2, so ID1's equivalent IDs also equal to ID2's
        // equivalent IDs.
        ArrayList<String> vars = new ArrayList<>(inv.getVarsAndVals());
        String id1 = vars.get(0);
        String id2 = vars.get(1);
        HashSet<String> eq1 = id2EquivalentIDs.get(id1);
        HashSet<String> eq2 = id2EquivalentIDs.get(id2);
        if (eq1.add(id2)) foundNewEq = true;
        if (eq1.addAll(eq2)) foundNewEq = true;
        if (eq2.add(id1)) foundNewEq = true;
        if (eq2.addAll(eq1)) foundNewEq = true;
      }
    }

    // Record the original invariants and the derived invariants.
    expandedInvs.addAll(invs);
    for (String id1 : id2EquivalentIDs.keySet()) {
      for (String id2 : id2EquivalentIDs.get(id1)) {
        if (id1.startsWith("nd") && id2.startsWith("nd")) {
          expandedInvs.add(new VariableEquality(id1, id2));
        }
      }
    }

    return expandedInvs;
  }

  /**
   * Select invariants with two variables.
   * @param invs A set of invariants to select.
   * @return A set of invariants with two access IDs.
   */
  private static HashSet<VariableEquality>
  getTwoVarInvs(HashSet<Invariant> invs) {
    HashSet<VariableEquality> twoVarInvs = new HashSet<>();
    for (Invariant inv : invs) {
      if (!(inv instanceof VariableEquality) || inv.getVars().size() != 2) {
        continue;
      }
      // Get the node IDs of the objects in the invariant.
      ArrayList<String> vars = new ArrayList<>(inv.getVars());
      String node1 = Variable.getNodeId(vars.get(0));
      String node2 = Variable.getNodeId(vars.get(1));

      // An access path will have a node ID, a constant will not.
      if (!node1.equals("") && !node2.equals("")) {
        twoVarInvs.add((VariableEquality) inv);
      }
    }

    return twoVarInvs;
  }

  /**
   * Write invariants to the specified file.
   * @param invs The invariants to write.
   * @param fileName The file to write to.
   */
  private static void
  writeInvsToFile(HashSet<VariableEquality> invs, String fileName) {
    Logger.info("Writing invariants to file " + fileName);
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
      for (VariableEquality inv : invs) {
        writer.write(inv.toString());
        writer.write("\n");
      }
    } catch (IOException ioe) {
      Logger.error("Failed to write invariants to file " + fileName);
      ioe.printStackTrace(System.err);
      System.exit(1);
    }
  }

  /**
   * Select invariants that involve two nodes, i.e., global invariants.
   * @param invs The set of invariants to select from.
   * @return A set of global invariants.
   */
  private static HashSet<VariableEquality>
  getGlobalInvariants(HashSet<VariableEquality> invs) {
    HashSet<VariableEquality> globalInvs = new HashSet<>();
    for (VariableEquality inv : invs) {
      // Two access ID should come from two nodes. When loading
      // invariants, constants will first be treated as "access IDs". But their
      // node ID will be "". Therefore, an invariant is not a global invariant
      // if it involves any constant, or two access IDs come from the same node.
      ArrayList<String> vars = new ArrayList<>(inv.getVars());
      String node1 = Variable.getNodeId(vars.get(0));
      String node2 = Variable.getNodeId(vars.get(1));
      if (node1.equals("") || node2.equals("") || node1.equals(node2)) {
        continue;
      }

      globalInvs.add(inv);
    }

    return globalInvs;
  }

  /**
   * Load invariants from the specified file.
   * @param invFileName The name of the invariant file.
   * @throws IOException If anything goes wrong when loading invariants..
   * @return A set of unique invariants.
   */
  private static HashSet<Invariant>
  loadInvsFromFile(String invFileName) throws IOException {
    HashSet<Invariant> invs = new HashSet<>();
    File invFile = new File(invFileName);
    try (BufferedReader reader = new BufferedReader(new FileReader(invFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // Skip lines that are not related to invariants.
        if (line.length() == 0
                || line.startsWith("Daikon version")
                || line.startsWith("Reading declaration")
                || line.startsWith("===================")
                || line.startsWith("Exiting Daikon")
                || line.contains(":::")) {
          continue;
        }

        // Parse the invariant string.
        Invariant inv = Invariant.parse(line);
        if (inv != null) {
          invs.add(inv);
        }
      }
    }
    return invs;
  }
}
