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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class DumpPoint {
	public static class Variable {
		public String name;
		public String type;
		public String value;

		public Variable(String n, String t, String v) {
			name = n;
			type = t;
			value = v;
		}
	}

	public String id;
	private HashMap<String, Variable> vars = new HashMap<>();

	public DumpPoint(String id) {
		this.id = id;
	}

	public void addVar(String name, String type, String value) {
		Variable var = new Variable(name, type, value);
		vars.put(name, var);
	}

	public boolean removeVar(String name) {
		return vars.remove(name) != null;
	}

	public Collection<Variable> getVars() {
		return vars.values();
	}

	public String valOfVar(String name) {
		Variable v = vars.get(name);
		if (v != null) {
			return v.value;
		} else {
			return null;
		}
	}

	/**
	 * Get the node ID (nd###) from this dump point's ID. The ID of a dump
	 * point always ends with "_nd###".
	 */
	public String getNodeId() {
		int lastUnderscoreIndex = this.id.lastIndexOf('_');
		return this.id.substring(lastUnderscoreIndex + 1);
	}
}
