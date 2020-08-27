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
package cofi.invariant;

import cofi.util.StrOps;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public class VariableEquality extends Invariant {
	public String varName1;
	public String varName2;

	public VariableEquality(String inv) {
		int eqSignIndex = inv.indexOf(" == ");
		varName1 = inv.substring(0, eqSignIndex);
		varName2 = inv.substring(eqSignIndex + 4);
	}

	public VariableEquality(String id1, String id2) {
		varName1 = id1;
		varName2 = id2;
	}

	/**
	 * Checks whether this equal-variables invariant holds at the given state.
	 * This equal-variables invariant holds at a state if:
	 * 1. Neither variable has a prefix in the state.
	 * 2. Both variables exist in the state, and both variables have the same
	 *    value.
	 * 3. Both variables have a prefix in the state. These two prefixes differ
	 *    only in their node IDs. And these two prefixes have the same value.
	 * @param state The state to check.
	 * @return True if the invariant holds.
	 */
	@Override
	public boolean holdsAt(HashMap<String, String> state) {
		// Get the variables or their prefixes in the state.
		String name1 = null, value1 = null, name2 = null, value2 = null;
		for (String varName : state.keySet()) {
			if (varName1.startsWith(varName) || varName.startsWith(varName1)) {
				name1 = varName;
				value1 = state.get(name1);
			} else if (varName2.startsWith(varName) || varName.startsWith(varName2)) {
				name2 = varName;
				value2 = state.get(name2);
			}
		}

		// Case 1: Neither variable has a prefix in the state.
		if (name1 == null && name2 == null) {
			return true;
		}

		// Otherwise, if only one variable has a prefix in the state, the invariant
		// does not hold in the state.
		if (name1 == null || name2 == null) {
			return false;
		}

		// If we are here, then both variables have their prefixes in the state.

		// Case 2: Both variables exist and have the same value.
		if (name1.equals(varName1)
						&& name2.equals(varName2)
						&& Objects.equals(value1, value2)) {
			return true;
		}

		// Case 3: Both variables have the same prefix (excluding the node IDs), and
		// the same value.
		return Objects.equals(StrOps.rmNodeID(name1), StrOps.rmNodeID(name2))
						&& Objects.equals(value1, value2);
	}

	/**
	 * Checks whether either of the variables is on the given node.
	 * @param nodeID The ID of a node.
	 * @return
	 */
	@Override
	public boolean involvesNode(String nodeID) {
		return varName1.startsWith(nodeID) || varName2.startsWith(nodeID);
	}

	@Override
	public HashSet<String> getVars() {
		HashSet<String> vars = new HashSet<>();
		vars.add(varName1);
		vars.add(varName2);
		return vars;
	}

	@Override
	public HashSet<String> getVarsAndVals() {
		return getVars();
	}

	@Override
	public HashSet<String> getNodes() {
		HashSet<String> nodes = new HashSet<>();
		nodes.add(StrOps.getNodeID(varName1));
		nodes.add(StrOps.getNodeID(varName2));
		return nodes;
	}

	@Override
	public String toString() {
		return varName1 + " == " + varName2;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VariableEquality)) {
			return false;
		}

		VariableEquality another = (VariableEquality) o;

		return (this.varName1.equals(another.varName1) && this.varName2.equals(another.varName2))
				|| (this.varName1.equals(another.varName2) && this.varName2.equals(another.varName1));
	}

	@Override
	public int hashCode() {
		int hash1 = varName1.hashCode(), hash2 = varName2.hashCode();
		if (hash1 > hash2) {
			return (varName1 + " == " + varName2).hashCode();
		} else {
			return (varName2 + " == " + varName1).hashCode();
		}
	}
}
