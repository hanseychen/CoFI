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

import java.util.HashMap;

public class HandleEvent extends Event {
	String sender;
	String receiver;
	String msgID;
	HashMap<String, String> oldState = new HashMap<>();
	HashMap<String, String> newState = new HashMap<>();

	/**
	 * Construct a HandleEvent and initialize its payload based on the given
	 * string. The given string will be in the following form:
   * sender receiver msgID oldCheckpoint newCheckpoint
	 * Both the oldCheckpoint and the new Checkpoint has the following form:
	 * varCnt (varName#####varValue#####)*varCnt
	 * @param payloadString The string representation of the payload.
	 * @return A partially initialized HandleEvent.
   * TODO: Try to make this method shorter.
	 */
	public static HandleEvent parse(String payloadString) {
		HandleEvent handleEvent = new HandleEvent();

		// Get the sender.
		int head = 0, tail = payloadString.indexOf(" ");
		handleEvent.sender = payloadString.substring(head, tail);

		// Get the receiver.
		head = tail + 1;
		tail = payloadString.indexOf(" ", head);
		handleEvent.receiver = payloadString.substring(head, tail);

		// Get the msg ID.
		head = tail + 1;
		tail = payloadString.indexOf(" ", head);
		handleEvent.msgID = payloadString.substring(head, tail);

		// Get the variable count in the old state.
		head = tail + 1;
		tail = payloadString.indexOf(" ", head);
		int varCnt = Integer.parseInt(payloadString.substring(head, tail));

		// Get the variables in the old state.
		head = tail + 1;
		for (int i = 0; i < varCnt; ++i) {
			tail = payloadString.indexOf("#####", head);
			String varName = payloadString.substring(head, tail);

			head = tail + 5;
			tail = payloadString.indexOf("#####", head);
			String varValue = payloadString.substring(head, tail);

			handleEvent.oldState.put(varName, varValue);
			head = tail + 5;
		}

		// Get the variable count in the new state.
		head = head + 1;
		tail = payloadString.indexOf(" ", head);
		if (tail == -1) {
			// If the handler node has no variable, the count will be 0, and there's
			// nothing after it in the string.
			return handleEvent;
		} else {
			varCnt = Integer.parseInt(payloadString.substring(head, tail));
		}

		// Get the variables in the new state.
		head = tail + 1;
		for (int i = 0; i < varCnt; ++i) {
			tail = payloadString.indexOf("#####", head);
			String varName = payloadString.substring(head, tail);

			head = tail + 5;
			tail = payloadString.indexOf("#####", head);
			String varValue = payloadString.substring(head, tail);

			handleEvent.newState.put(varName, varValue);
			head = tail + 5;
		}

		return handleEvent;
	}

	@Override
	public String toString() {
		return super.toString() + " " +
						sender + " " +
						receiver + " " +
						msgID + " " +
						oldState + " " +
						newState;
	}
}
