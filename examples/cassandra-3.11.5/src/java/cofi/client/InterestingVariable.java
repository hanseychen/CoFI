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

/**
 * User-specified interesting variable.
 */
public class InterestingVariable {
  private AccessPath accessPath;
  private String metadata;

  /**
   * Create an InterestingVariable according to one line from the
   * interesting-variables.txt file.
   * @param line One line from the interesting-variables.txt file.
   */
  public InterestingVariable(String line) {
    // Find the separator between the access path and the metadata.
    int colonIndex = line.indexOf(":");
    if (colonIndex == -1) {
      throw new IllegalArgumentException("The specified string does not have " +
              "a colon separator: " + line);
    }

    // Extract the metadata
    metadata = line.substring(colonIndex + 2);

    // Extract the access path
    accessPath = new AccessPath(line.substring(0, colonIndex));
  }

  public AccessPath getAccessPath() {
    return accessPath;
  }
}
