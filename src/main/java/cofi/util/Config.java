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
package cofi.util;

/**
 * CoFI's configurations.
 * TODO: Load configurations from a YAML file.
 */
public class Config {
  // The fault injection engine's address.
	// 3 = c, 15 = o, 6 = f, 9 = i. 31569 = cofi. :P
	public static final int FI_ENGINE_PORT = 31569;

	// The max number of times to retry the same network partition scenario.
	public static final int MAX_RETRIES_PER_SCENARIO = 5;

	// The max number of test runs for each invariant.
	public static final int MAX_ITERATIONS_PER_INVARIANT = 101;

	////////////////
	// File names //
	////////////////
	// The file storing the iterations run for each invariants.
	public static final String FILE_NAME_TOTAL_RUNS = "/app/total-runs.txt";
	// The file storing the ranked invariants.
	public static final String FILE_NAME_SELECTED_INVARIANTS = "/cofi/selected-invariants.txt";
}
