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

import cofi.util.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merge the profiling logs into dtrace files.
 */
public class LogMerger {
	private static final String kLogFileNameRex = "td\\d+_nd-?\\d+.txt";
	private static HashMap<String, HashSet<DumpPoint>> allSenderDumpPoints =
					new HashMap<>();
	private static HashMap<String, HashSet<DumpPoint>> allReceiverDumpPoints =
					new HashMap<>();
	private static int nullMsgCnt = 0;
	private static int maxDpCnt = 0;
	private static int maxVarCnt = 0;
	private static HashSet<String> pdpIds = new HashSet<>();

	/**
	 * Generate the dtrace files using the logs in the specified directory.
	 */
	public static void run(String logDir) {
		try {
			loadLogs(logDir);
			mergeLogs();
			daikonizeLogs();
		} catch (Throwable t) {
			Logger.fatal("Unexpected exception when merging logs.");
			t.printStackTrace(System.err);
			Runtime.getRuntime().halt(1);
		}
	}

	/**
	 * Load profile logs in the specified directory.
	 */
	private static void loadLogs(String logDirName) throws IOException {
		// Get a list of log files. Their names are in the format of td\d+_nd\d+.txt
		Pattern pattern = Pattern.compile(kLogFileNameRex);
		File logDir = new File(logDirName);
		File[] files = logDir.listFiles();
		int logFileCnt = 0;
		for (File file : files) {
			String fileName = file.getName();
			Matcher matcher = pattern.matcher(fileName);
			if (!matcher.find()) {
				continue;
			}

			logFileCnt++;
			loadLogsFromFile(file);
		}
		Logger.info("Found " + logFileCnt + " log files.");

		// Make sure that
		// 1. Some messages are received;
		// 2. All the received messages are sent.
    /*
		if (allReceiverDumpPoints.size() == 0) {
			throw new RuntimeException("No message is received!");
		}
		for (String msg : allReceiverDumpPoints.keySet()) {
			if (!allSenderDumpPoints.containsKey(msg)) {
				Logger.warn("Received a message that is never sent: " + msg);
			}
		}
		*/
		Logger.info("Found " + allSenderDumpPoints.size() +
						" message sending events.");
		Logger.info("Found " + allReceiverDumpPoints.size() +
						" message receiving events.");
	}

	/**
	 * Load profile logs from the specified file.
	 */
	private static void loadLogsFromFile(File logFile) throws IOException {
	  Logger.debug("Loading logs from file " + logFile);
		try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
			// Maintain a reference for the init dump point and each receiver-side
			// dump point. Later this function will remove the variables that are
			// never changed.
			ArrayList<DumpPoint> regularDumpPoints = new ArrayList<>();
			DumpPoint initDumpPoint = null;

			// Load the content
			String action;
			// Each iteration, this loop loads all the dump points for a given action.
			while ((action = reader.readLine()) != null) {
				// Load the message ID and make sure its unique,
				// i.e., sent at most once and received at most once.
				String messageId = reader.readLine();
				if (!messageId.equals("null")) {
					if (action.equals("send")) {
						if (allSenderDumpPoints.containsKey(messageId)) {
							throw new RuntimeException("Double sending message " + messageId);
						}
						allSenderDumpPoints.put(messageId, new HashSet<DumpPoint>());
					} else if (action.equals("receive")) {
						if (allReceiverDumpPoints.containsKey(messageId)) {
							throw new RuntimeException("Double receiving message " + messageId);
						}
						allReceiverDumpPoints.put(messageId, new HashSet<DumpPoint>());
					} else if (action.equals("init")) {
						// No-op
					}
				} else {
					nullMsgCnt++;
				}

				// Load the dump points associated with this action.
				int dpCnt = Integer.parseInt(reader.readLine());
				for (int dpIdx = 0; dpIdx < dpCnt; ++dpIdx) {
					String dpId = reader.readLine();
					DumpPoint dp = new DumpPoint(dpId);

					// Load the variables dumped at this dump point.
					int varCnt = Integer.parseInt(reader.readLine());
					for (int varIdx = 0; varIdx < varCnt; ++varIdx) {
						String varName = reader.readLine();
						String varType = reader.readLine();
						String varValue = reader.readLine();

						// Hack for Cassandra.
						if (varName.endsWith(").value")
										&& !varName.endsWith("(-1839152142).value")
										&& !varName.endsWith("(1816706834).value")) {
							continue;
						} else if (varName.contains("keyspaceInstances.get(")
										&& !varName.contains("keyspaceInstances.get(-1422455755)")) {
							continue;
						}

						dp.addVar(varName, varType, varValue);
					}

					// Record that the dump point is with this action.
					if (!messageId.equals("null")) {
						if (action.equals("send")) {
							allSenderDumpPoints.get(messageId).add(dp);
							regularDumpPoints.add(dp);
						} else if (action.equals("receive")) {
							allReceiverDumpPoints.get(messageId).add(dp);
							regularDumpPoints.add(dp);
						} else if (action.equals("init")) {
							initDumpPoint = dp;
						}
					}

					// For debug only
					if (varCnt > maxVarCnt) {
						maxVarCnt = varCnt;
					}
				}

				// For debug only
				if (dpCnt > maxDpCnt) {
					maxDpCnt = dpCnt;
				}

				// Read the tail empty line.
				reader.readLine();
			}

			// If this thread did not handle any method, we will not have the init dump point.
			if (initDumpPoint == null) {
				return;
			}

			Logger.debug("File " + logFile.getName() + " has an initial dump point.");
			Logger.debug("There are " + initDumpPoint.getVars().size() + " variables in the initial dump point.");
			Logger.debug("File " + logFile.getName() + " has " + regularDumpPoints.size() + " regular dump points.");
			/*
			int unchangedVarCount = 0;
			int removedVarCount = 0;
			// Remove from the receiver dump points the variables that are never changed.
			for (DumpPoint.Variable initVar : initDumpPoint.getVars()) {
				boolean varChanged = false;
				for (DumpPoint dp : regularDumpPoints) {
					// Go over each variable to see if the initial variable is changed in this dump point.
					for (DumpPoint.Variable laterVar : dp.getVars()) {
						// If the variable has the same name but different value, the
						// variable in the initial dump point is changed.
						if (initVar.name.equals(laterVar.name)) {
							if (!initVar.value.equals(laterVar.value)) {
								varChanged = true;
								break;
							}
						}
						// Otherwise, if one variable's name is the prefix of the other's,
						// the variable is changed.
						else if (initVar.name.startsWith(laterVar.name)
										|| laterVar.name.startsWith(initVar.name)) {
							varChanged = true;
							break;
						}
					}

					// If the variable is changed, do not check the remaining dump points.
					// Leave it in the dump point so that we can mine invariants on it.
					if (varChanged) {
						break;
					}
				}

				// If this variable is never changed in any dump points, remove it to
				// save invariant mining time.
				if (!varChanged) {
					unchangedVarCount++;
					for (DumpPoint dp : regularDumpPoints) {
						if (dp.removeVar(initVar.name)) {
							removedVarCount++;
						}
					}
				}
			}
			Logger.debug(unchangedVarCount + " variables are never changed.");
			Logger.debug("Removed " + removedVarCount + " variables from file " + logFile.getName() + ".");
			*/
		}
	}

	/**
	 * The DumpPoint ID will end with string "_td##_nd##" where "##" stands a number.
	 * @param dumpPointID
	 * @return
	 */
	private static String getThreadIDFromDumpPointID(String dumpPointID) {
		int threadIDStartIndex = dumpPointID.lastIndexOf("_td");
		return dumpPointID.substring(threadIDStartIndex + 1);
	}

	private static String fileNameForDumpPoint(String pdpId) {
		return "tmpLogs/" + pdpId + ".log";
	}

	private static
	BufferedWriter getAppendWriterForPairedDumpPoint(String pdpId)
					throws IOException {
		return new BufferedWriter(
						new FileWriter(fileNameForDumpPoint(pdpId), true));
	}

	/**
	 * Append a variable to the temporary profile log file for the specified
	 * paired dump point.
	 */
	private static void appendVarToPairedDumpPointFile(
					String name, String type, String value, String pdpId)
					throws IOException {
		try (BufferedWriter writer = getAppendWriterForPairedDumpPoint(pdpId)) {
			writer.write(name + "\n");
			writer.write(type + "\n");
			writer.write(value + "\n");
		}
	}

	/**
	 * Append a variable to the temporary profile log file for the specified
	 * paired dump point.
	 */
	private static void
	appendNewDumpPointDelimiterToPairedDumpPointFile(String pdpId)
					throws IOException {
		try (BufferedWriter writer = getAppendWriterForPairedDumpPoint(pdpId)) {
			writer.write("\n");
		}
	}

	/**
	 * Merge the loaded logs.
	 * This method works by merging each receiver dump point
	 * with its corresponding sender dump point (with the same message ID).
	 * For each pair of dump points, it combines each dump point at the sender
	 * side with each dump point at the receiver side.
	 */
	private static void mergeLogs() throws IOException {
		int msgCnt = 0;
		int varCnt = 0;
		int pdpCnt = 0;
		for (String msgId : allReceiverDumpPoints.keySet()) {
			Logger.debug("Working on message " + ++msgCnt);
			HashSet<DumpPoint> receiverDumpPoints = allReceiverDumpPoints.get(msgId);
			HashSet<DumpPoint> senderDumpPoints = allSenderDumpPoints.get(msgId);

			if (receiverDumpPoints == null) {
				Logger.warn("The receiverDumpPoints for " + msgId + " is null.");
				continue;
			}
			if (senderDumpPoints == null) {
				Logger.warn("The senderDumpPoints for " + msgId + " is null.");
				continue;
			}

			// Go over each sender-receiver pair to create the paired dump points.
			for (DumpPoint rdp : receiverDumpPoints) {
				for (DumpPoint sdp : senderDumpPoints) {
					String pairedDumpPointId = sdp.id + "_and_" + rdp.id;
					pdpIds.add(pairedDumpPointId);
					DumpPoint pdp = new DumpPoint(pairedDumpPointId);

					appendNewDumpPointDelimiterToPairedDumpPointFile(pdp.id);

					pdpCnt++;
					if (pdpCnt % 10 != 0) {
						continue;
					}

					// Fill in the variables on the sender side.
					for (DumpPoint.Variable v : sdp.getVars()) {
						appendVarToPairedDumpPointFile(
										sdp.getNodeId() + "-" + v.name,
										v.type, v.value, pdp.id);
						varCnt++;
					}

					// Fill in the variables on the receiver side.
					for (DumpPoint.Variable v : rdp.getVars()) {
						appendVarToPairedDumpPointFile(
										rdp.getNodeId() + "-" + v.name,
										v.type, v.value, pdp.id);
						varCnt++;
					}
				}
			}
		}

		Logger.debug("The resulting logs have " + varCnt + " variables.");
		Logger.debug("The resulting logs have " + pdpIds.size() +
						" unique paired dump points.");
		Logger.debug("The resulting logs have " + pdpCnt +
						" paired dump point instances.");
	}

	/**
	 * Process each temporary profile log to make it readable for daikon.
	 */
	private static void daikonizeLogs() throws IOException {
		int pdpIdx = 0;
		for (String pdpId : pdpIds) {
			Logger.info("Translating temporary log file #" + ++pdpIdx);
			daikonizeLog(pdpId);
		}
	}

	private static void getAllVarsFromLog(
					ArrayList<DumpPoint.Variable> vars,
					ArrayList<DumpPoint> dumpPoints,
					String logFileName)
					throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(logFileName));
		DumpPoint curDp = null;
		while (true) {
			String line = reader.readLine();
			if (line == null) {
				break;
			} else if (line.equals("")) {
				// Finish the old dump point and start a new one.
				if (curDp != null && curDp.getVars().size() != 0) {
					dumpPoints.add(curDp);
				}
				curDp = new DumpPoint("not-used");
			} else {
				// Record the variable. Replace prefix.
				String type = reader.readLine(); // type
				String value = reader.readLine(); // value
				DumpPoint.Variable newVar = new DumpPoint.Variable(line, type, value);
				vars.add(newVar);

				// Record the variable into the current dump point.
				if (curDp == null) {
					throw new IllegalStateException("curDp shouldn't be null.");
				}
				curDp.addVar(line, type, value);
			}
		}

		// Remove the prefixes.
		for (int i = 0; i < vars.size();) {
			boolean removed = false;
			for (int j = i + 1; j < vars.size(); ++j) {
				// If vars[i]'s name is the prefix of vars[j],
				// there's no need to keep track of vars[i] separately.
				if (vars.get(j).name.startsWith(vars.get(i).name)) {
					vars.remove(i);
					removed = true;
					break;
				}
			}
			// If vars[i] is not a prefix of any of the vars,
			// repeat the checking for next var.
			if (!removed) {
				i++;
			}
		}
	}

	private static void daikonizeLog(String pdpId) throws IOException {
		String logFileName = fileNameForDumpPoint(pdpId);

		// Load variables from the temporary log.
		ArrayList<DumpPoint.Variable> vars = new ArrayList<>();
		ArrayList<DumpPoint> dumpPoints = new ArrayList<>();
		getAllVarsFromLog(vars, dumpPoints, logFileName);

		// Go over the log again, and translate it to daikon's trace.
		// 0. Create the log file.
		try (BufferedWriter writer = new BufferedWriter(
						new FileWriter("dtraces/" + pdpId + ".dtrace"))) {
			// 1. Write the declarations.
			// 1.1. Specify the program point.
			writer.write("decl-version 2.0\n");
			writer.write("var-comparability none\n\n");
			writer.write("ppt " + pdpId + ":::UNUSED\n");
			writer.write("ppt-type point\n");
			// 1.2. Declare the variables.
			for (DumpPoint.Variable v : vars) {
				writer.write("variable " + v.name + "\n");
				writer.write("var-kind variable\n");
				writer.write("dec-type " + v.type + "\n");
				writer.write("rep-type " + v.type + "\n");
				writer.write("comparability -1\n");;
			}
			writer.write('\n');

			// 2. Write the data trace records
			int recordIdx = 0;
			for (DumpPoint dp : dumpPoints) {
				// 2.1. Program point name, i.e., dump point ID.
				writer.write(pdpId + ":::UNUSED\n");
				// 2.2. Nonce
				writer.write("this_invocation_nonce\n");
				writer.write(recordIdx++ + "\n");
				// 2.3. Variable details.
				for (DumpPoint.Variable v : vars) {
					// 2.3.1. Variable name.
					writer.write(v.name + "\n");
					// 2.3.2. Variable value.
					String value = dp.valOfVar(v.name);
					if (value != null) {
						if (v.type.equals("string")) {
							writer.write("\"" + value + "\"\n");
						} else {
							writer.write(value + "\n");
						}
					} else {
						switch (v.type) {
							case "int":
								writer.write("0\n");
								break;
							case "float":
								writer.write("0.0\n");
								break;
							case "boolean":
								writer.write("false\n");
								break;
							case "string":
								writer.write("\"\"\n");
								break;
							default:
								throw new IllegalStateException("Unrecognized variable type!");
						}
					}
					// 2.3.3. Modified? Daikon says it's safe to always specify 1:
					// https://plse.cs.washington.edu/daikon/download/doc/developer/File-formats.html#Data-trace-records
					writer.write(1 + "\n");
				}
				writer.write("\n");
			}
		}
	}
}
