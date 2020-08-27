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

public class UpdateEvent extends Event {
	public String varName;
	public String varValue;

	/**
	 * Construct an UpdateEvent and initialize its payload based on the given
	 * string. The given string will be in the following form:
	 * name#####value#####
	 * @param payloadString The string representation of the payload.
	 * @return A partially initialized UpdateEvent.
	 */
	public static UpdateEvent parse(String payloadString) {
		UpdateEvent updateEvent = new UpdateEvent();

		int head = 0, tail = payloadString.indexOf("#####");
		updateEvent.varName = payloadString.substring(head, tail);

		head = tail + 5;
		tail = payloadString.indexOf("#####", head);
		updateEvent.varValue = payloadString.substring(head, tail);

		return updateEvent;
	}

	@Override
	public String toString() {
		return super.toString() + " " + varName + " = " + varValue;
	}
}
