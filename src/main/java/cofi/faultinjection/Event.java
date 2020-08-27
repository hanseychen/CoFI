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

import cofi.util.Logger;
import cofi.util.StrOps;

public class Event {
	int id;
	String nid;
	String tid;
	String eventType;

	/**
	 * Create an Event object based on the given string, as well as the given
	 * event ID.
	 * @param eventStr The string representation of the Event.
	 * @param eventID The ID to assign to the new event.
	 * @return The created Event object.
	 */
	public static Event parse(String eventStr, int eventID) {
	  eventStr = eventStr.trim();

		// Get the thread and node IDs.
		int head = 0, tail = eventStr.indexOf(" ");
		String reporterThreadGUID = eventStr.substring(head, tail);
		String tid = StrOps.getThreadIDFromGUID(reporterThreadGUID);
		String nid = StrOps.getNodeIDFromGUID(reporterThreadGUID);

		// Get the event type.
		head = tail + 1;
		tail = eventStr.indexOf(" ", head);
		String eventType = eventStr.substring(head, tail);

		// Create the event based on the event type and the remaining string.
		Event event = null;
		switch (eventType) {
			case "updateVariable":
			  // UpdateEvent: Get the updated variable's name and value.
				event = UpdateEvent.parse(eventStr.substring(tail + 1));
				break;
			case "send":
			  // SendEvent: Get the sender, receiver, msg ID, and stack Hash.
				event = SendEvent.parse(eventStr.substring(tail + 1));
				break;
			case "messageHandling":
				event = HandleEvent.parse(eventStr.substring(tail + 1));
				break;
			default:
				Logger.fatal("Unrecognized event type: " + eventType);
				Runtime.getRuntime().halt(0);
		}
		event.id = eventID;
		event.tid = tid;
		event.nid = nid;
		event.eventType = eventType;
		return event;
	}

	@Override
	public String toString() {
		return "[" + id + "] " + tid + "@" + nid + " " + eventType;
	}
}
