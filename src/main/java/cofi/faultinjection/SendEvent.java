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

public class SendEvent extends Event {
	String sender;
	String receiver;
	String msgID;
	String stackHash;

	/**
	 * Construct a SendEvent and initialize its payload based on the given
	 * string. The given string will be in the following form:
   * sender receiver msgID stackHash
	 * @param payloadString The string representation of the payload.
	 * @return A partially initialized SendEvent.
	 */
	public static SendEvent parse(String payloadString) {
		SendEvent sendEvent = new SendEvent();

		int head = 0, tail = payloadString.indexOf(" ");
		sendEvent.sender = payloadString.substring(head, tail);

		head = tail + 1;
		tail = payloadString.indexOf(" ", head);
		sendEvent.receiver = payloadString.substring(head, tail);

		head = tail + 1;
		tail = payloadString.indexOf(" ", head);
		sendEvent.msgID = payloadString.substring(head, tail);

		head = tail + 1;
		sendEvent.stackHash = payloadString.substring(head);

		return sendEvent;
	}

	@Override
	public String toString() {
		return super.toString() + " " +
						sender + " " +
						receiver + " " +
						msgID + " " +
						stackHash;
	}
}
