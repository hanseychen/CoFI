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
package cofi.variable;

public class Variable {
	public String name;
	public String type;
	public String value;

  public static String getNodeId(String varFullName) {
    // Assuming variables start with their node IDs.
    if (varFullName.startsWith("nd")) {
      int firstHyphenIndex = varFullName.indexOf('-', 3);
      return varFullName.substring(0, firstHyphenIndex);
    } else {
      // Otherwise, it is a constant. Constant doesn't have a node ID.
      return "";
    }
  }

  public static String getVarName(String varFullName) {
    // Assuming variables start with their node IDs.
    if (varFullName.startsWith("nd")) {
      int firstHyphenIndex = varFullName.indexOf('-', 3);
      return varFullName.substring(firstHyphenIndex + 1);
    } else {
      // Otherwise, it is a constant. Just return it.
      return varFullName;
    }
  }

  /**
   * Get the last accessor of the specified access ID.
   * @param accessID An access ID whose end accessor is of interest.
   * @return The end accessor of the specified access ID.
   */
  public static String getEndAccessor(String accessID) {
    String[] accessors = accessID.split("\\.");
    return accessors[accessors.length - 1];
  }
}
