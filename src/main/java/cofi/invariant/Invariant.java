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

import java.util.HashMap;
import java.util.HashSet;

/**
 * The super class for all types of invariants.
 */
public abstract class Invariant {
	/**
	 * A predicate for checking whether the current invariant holds at the given
	 * state.
	 * @param state The state to check.
	 * @return True if the invariant holds.
	 */
	public abstract boolean holdsAt(HashMap<String, String> state);

	/**
	 * Get the variables involved in this invariant.
	 * @return The set of variables involved.
	 */
	public abstract HashSet<String> getVars();

	/**
	 * Get the variables and values in this invariant.
	 * @return The set of variables and values.
	 */
	public abstract HashSet<String> getVarsAndVals();

	/**
	 * Get the nodes involved in this invariant.
	 * @return The set of nodes involved.
	 */
	public abstract HashSet<String> getNodes();

	/**
	 * A predicate for checking whether this invariant involves the given node.
	 * @param nodeID The ID of a node.
	 * @return Whether this invariant involves the given node.
	 */
	public abstract boolean involvesNode(String nodeID);


	/**
	 * Create an invariant checker based on the given invariant string.
	 * @param invStr The string representation of the invariant.
	 * @return The created invariant checker.
	 * TODO: Support one of invariants.
	 */
	public static Invariant parse(String invStr) {
		if (invStr.contains(" == nd")) {
			return new VariableEquality(invStr);
		} else if (invStr.contains(" == ")) {
			return new ConstantVariable(invStr);
		}
		return null;
	}
}
