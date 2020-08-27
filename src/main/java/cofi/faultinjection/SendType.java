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

/**
 * This class represents the types of send events. The type of a send event is
 * characterized by the following four aspects of a send event:
 * 1. The sender of the corresponding message.
 * 2. The receiver of the corresponding message.
 * 3. The runtime call stack of the send method.
 * 4. The global state when the message is sent.
 */
public class SendType {
	public String sender, receiver, stackHash;
	public HashMap<String, String> startState = new HashMap<>();
	// The end state after the corresponding message is handled. This info is not
	// used to characterize the send event, but to enhance debugging.
	public HashMap<String, String> endState = new HashMap<>();

	public static SendType parse(String eventString) {
		int fromIndex = 0, toIndex = 0;
		SendType type = new SendType();

		// Parse the sender
		fromIndex = "sender#####".length();
		toIndex = eventString.indexOf("#####receiver#####", fromIndex);
		type.sender = eventString.substring(fromIndex, toIndex);

		// Load the receiver
		fromIndex = toIndex + "#####receiver#####".length();
		toIndex = eventString.indexOf("#####stack#####", fromIndex);
		type.receiver = eventString.substring(fromIndex, toIndex);

		// Load the stack
		fromIndex = toIndex + "#####stack#####".length();
		toIndex = eventString.indexOf("#####fromStateVarCnt#####", fromIndex);
		type.stackHash = eventString.substring(fromIndex, toIndex);

		// Load start state
		fromIndex = toIndex + "#####fromStateVarCnt#####".length();
		toIndex = eventString.indexOf("#####name#####", fromIndex);
		int varCnt = Integer.valueOf(eventString.substring(fromIndex, toIndex));
		for (int varIndex = 0; varIndex < varCnt; ++varIndex) {
			fromIndex = toIndex + "#####name#####".length();
			toIndex = eventString.indexOf("#####value#####", fromIndex);
			String varName = eventString.substring(fromIndex, toIndex);
			fromIndex = toIndex + "#####value#####".length();
			toIndex = eventString.indexOf("#####", fromIndex);
			String varValue = eventString.substring(fromIndex, toIndex);
			type.startState.put(varName, varValue);
		}

		// Load end state
		/*
		fromIndex = toIndex + "#####toStateVarCnt#####".length();
		toIndex = eventString.indexOf("#####name#####", fromIndex);
		varCnt = Integer.valueOf(eventString.substring(fromIndex, toIndex));
		for (int varIndex = 0; varIndex < varCnt; ++varIndex) {
			fromIndex = toIndex + "#####name#####".length();
			toIndex = eventString.indexOf("#####value#####", fromIndex);
			String varName = eventString.substring(fromIndex, toIndex);
			fromIndex = toIndex + "#####value#####".length();
			toIndex = eventString.indexOf("#####", fromIndex);
			String varValue = eventString.substring(fromIndex, toIndex);
			event.endState.put(varName, varValue);
		}
		*/

		return type;
	}

	/**
	 * Check if the message associates with this send type would be sent or
	 * received by the given node.
	 * @param nodeID The ID of the node to check.
	 * @return Whether the message would be sent or received by the given node.
	 */
	boolean msgSentOrReceivedBy(String nodeID) {
		return sender.equals(nodeID) || receiver.equals(nodeID);
	}

	@Override
	public int hashCode() {
		return sender.hashCode()
						^ receiver.hashCode()
						^ stackHash.hashCode()
						^ startState.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SendType)) {
			return false;
		}

		SendType another = (SendType) o;
		return this.sender.equals(another.sender)
						&& this.receiver.equals(another.receiver)
						&& this.stackHash.equals(another.stackHash)
						&& this.startState.equals(another.startState);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("sender#####").append(sender);
		sb.append("#####receiver#####").append(receiver);
		sb.append("#####stack#####").append(stackHash);

		sb.append("#####fromStateVarCnt#####").append(startState.size());
		for (String varName : startState.keySet()) {
			sb.append("#####name#####").append(varName);
			sb.append("#####value#####").append(startState.get(varName));
		}

		sb.append("#####toStateVarCnt#####").append(endState.size());
		for (String varName : endState.keySet()) {
			sb.append("#####name#####").append(varName);
			sb.append("#####value#####").append(endState.get(varName));
		}
		sb.append("#####");

		return sb.toString();
	}
}
