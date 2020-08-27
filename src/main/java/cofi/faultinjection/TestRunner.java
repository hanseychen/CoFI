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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

public class TestRunner implements Runnable {
	private String runScript;
	private boolean running = true;
	StringBuilder scriptOutput = new StringBuilder();

	public TestRunner(String script) {
		runScript = script;
	}

	public boolean isRunning() {
		return running;
	}

	@Override
	public void run() {
		try {
			runMayThrow();
		} catch (Throwable t) {
			die("Error when running worker.", t);
		}
	}

	private void runMayThrow() throws IOException {
		// Start a new test run.
		ProcessBuilder pb = new ProcessBuilder(runScript);
		pb.redirectErrorStream(true);
		Process pRunScript = pb.start();

		// Pass along the workload's output.
		BufferedReader workerLogs = new BufferedReader(new
						InputStreamReader(pRunScript.getInputStream()));
		String workerLog;
		while (true) {
			workerLog = workerLogs.readLine();
			if (workerLog == null) {
				break;
			} else {
				workerLog += "\n";
				scriptOutput.append(workerLog);
				System.out.print(workerLog);

				// If the checker fails, we have triggered a bug.
				// TODO: This should be configurable.
				if (workerLog.contains("test has failed")) {
					// The test case may be flaky.
					// If we haven't failed any message, don't consider this as a bug.
					//if (!EventManager.hasFailedMsgs) continue;

					Logger.info("Bug triggered.");
					if (Engine.isReplaying()) {
						// Wait for user acknowledgement before we exit.
						System.out.print("Press any key to exit.");
						System.in.read();
						System.exit(1);
					} else {
						recordTriggeringProcess();
						//System.exit(1);
					}
				}
			}
		}

		// Sleep for a while to let the remaining messages go through.
    try {
			Thread.sleep(3000);
		} catch (InterruptedException ie) {
    	// ignore.
		}

		running = false;
	}

	/**
	 * TODO: This is an ugly way to record the triggering process of a failure.
	 */
	private void recordTriggeringProcess() {
		long ticks = new Date().getTime();
		try (BufferedWriter writer = new BufferedWriter(
				new FileWriter("failure-plan-" + ticks + ".txt"))) {
			writer.write("Invariant:\n");
			writer.write(Engine.getInvariant() + "\n");
			writer.write("==========\n");

			// Record which run this bug is triggered.
			writer.write("\n");
			writer.write("Triggered at run " + Engine.getIteration());
			writer.write("\n");

			for (SendType e : EventManager.failedSendTypes) {
				writer.write(e.toString() + "\n");
			}

			// Record the execution output for manual debugging.
			writer.write("\n");
			writer.write(scriptOutput.toString());
			writer.flush();
		} catch (IOException ioe) {
			die("Fail to record the bug-triggering failure plan.", ioe);
		}
	}

	private void die(String reason, Throwable t) {
		System.err.print("[Worker] " + reason + "\n");
		t.printStackTrace(System.err);
		Runtime.getRuntime().halt(1);
	}
}


