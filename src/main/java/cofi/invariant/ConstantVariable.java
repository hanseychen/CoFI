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

public class ConstantVariable extends Invariant {
	public String varName;
	public String value;

	/**
	 * Create a ConstantVariable invariant object.
	 * The constant variable invariant is of the form "var == CONSTANT".
	 * @param invStr The string representation of the invariant.
	 */
	public ConstantVariable(String invStr) {
		int varNameEndIndex = invStr.indexOf(" == ");
		varName = invStr.substring(0, varNameEndIndex);
		value = StrOps.rmDoubleQuotes(invStr.substring(varNameEndIndex + 4).trim());
	}

	/**
	 * Checks whether this constant-variable invariant holds at the given state.
	 * This invariant holds only when the variable exists in the state and it
	 * equals to the constant value.
	 * @param state The state to check.
	 * @return True if this constant-variable invariant holds at the given state.
	 */
	@Override
	public boolean holdsAt(HashMap<String, String> state) {
	  return state.containsKey(varName) && value.equals(state.get(varName));
	}

	/**
	 * Check if the involved variable is on the given node.
	 * @param nodeID The ID of a node.
	 * @return The involved variable is on the given node.
	 */
	@Override
	public boolean involvesNode(String nodeID) {
		return varName.startsWith(nodeID);
	}

	@Override
	public HashSet<String> getVars() {
		HashSet<String> vars = new HashSet<>();
		vars.add(varName);
		return vars;
	}

	@Override
	public HashSet<String> getVarsAndVals() {
		HashSet<String> vs = new HashSet<>();
		vs.add(varName);
		vs.add(value);
		return vs;
	}

	@Override
	public HashSet<String> getNodes() {
		HashSet<String> nodes = new HashSet<>();
		nodes.add(StrOps.getNodeID(varName));
		return nodes;
	}

	@Override
	public String toString() {
		return varName + " == " + value;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ConstantVariable)) {
			return false;
		}

		ConstantVariable another = (ConstantVariable) o;

		return this.varName.equals(another.varName)
						&& this.value.equals(another.value);
	}
}
