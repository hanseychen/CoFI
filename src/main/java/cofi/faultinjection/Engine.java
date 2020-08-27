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

import cofi.invariant.Invariant;
import cofi.util.Config;
import cofi.util.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * CoFI's fault injection engine.
 */
public class Engine {
  private static Invariant invariant;
  private static String testCase;
  private static String cleanupScript;
  private static boolean replay = false;
  private static TestRunner testRunner = null;
  private static int iteration = 0;

  /**
   * Start the fault injection engine, and run the test case based on the
   * configuration and command line argument.
   * @param args Command line arguments.
   */
  public static void main(String[] args) {
    try {
      // Initialize the engine based on the arguments.
      init(args);

      // Start listening for the reports from the CoFI clients.
      ServerSocket ss = new ServerSocket(Config.FI_ENGINE_PORT);
      ss.setSoTimeout(500);

      // Start running the tests.
      while (++iteration <= Config.MAX_ITERATIONS_PER_INVARIANT) {
        Logger.info("===============");
        Logger.info("Test Run " + iteration);
        Logger.info("===============");

        // Step 1: Cleanup the leftover from last run.
        new ProcessBuilder(cleanupScript).start().waitFor();
        EventManager.refresh();

        // Step 2: Get a scenario of network partition for the current test run.
        // When replaying a failed test, we stick to the partition scenario
        // loaded from the failure file instead of creating a new one for each
        // run.
        if (!replay && !EventManager.createNewPartitionScenario()) {
          Logger.info("No more partition scenario to try. " +
                  "Finish testing the current invariant.");
          break;
        }
        // TODO: This method should be called in createNewPartitionScenario().
        EventManager.dumpCurFailureScenario();

        // Step 3: Run the test case and inject the network partition.
        testRunner = new TestRunner(testCase);
        new Thread(testRunner).start();
        while (testRunner.isRunning()) {
          try (Socket s = ss.accept();
               DataOutputStream out = new DataOutputStream(s.getOutputStream());
               DataInputStream in = new DataInputStream(s.getInputStream())) {
            // TODO: The event type is no longer used. Remove it.
            int eventType = in.readInt();
            switch (eventType) {
              case 2:
                handleEvent(in, out);
                break;
              default:
                Logger.fatal("Unsupported event type: " + eventType);
                System.exit(1);
            }
          } catch (SocketTimeoutException ste) {
            // ignore
          } catch (IOException ioe) {
            Logger.info("Got IOException when handling requests from a CoFI " +
                    "client. This is expected during cluster shutdown.", ioe);
          }
        }

        // Step 4: Record the new inconsistent states for future test runs.
        if (!replay) {
          EventManager.recordNewInconsistentStates();
        }
      }

      recordTotalIterations(invariant, iteration);
    } catch (Throwable t) {
      Logger.fatal("Unhandled exception during fault injection.", t);
      System.exit(1);
    }
  }

  /**
   * Record the number of iterations run for the specified invariant.
   * @param invariant The specified invariant.
   * @param n The number of iterations.
   */
  private static void recordTotalIterations(Invariant invariant, int n) {
    try (BufferedWriter writer = new BufferedWriter(
            new FileWriter(Config.FILE_NAME_TOTAL_RUNS, true))) {
      writer.write(n + " runs for invariant " + invariant + "\n");
    } catch (IOException ioe) {
      Logger.error("Failed to write to " + Config.FILE_NAME_TOTAL_RUNS, ioe);
      System.exit(1);
    }
  }

  /**
   * Initialize the engine based on the command line arguments. There can be
   * three or four arguments:
   * 1. A test case to run the target system and checks the output.
   * 2. A string representation of the invariant to test.
   * 3. A script to clean up after each test run.
   * 4. A file describing a network partition scenario to replay.
   * @param args The command line arguments.
   */
  private static void init(String[] args) {
    if (args.length != 3 && args.length != 4) {
      Logger.fatal("Wrong number of arguments: " + args.length);
      printUsage();
      Runtime.getRuntime().halt(1);
    }

    testCase = args[0];
    Logger.debug("Test case: " + testCase);

    // For convenience, give both Engine and EventManager a reference of the
    // current invariant.
    invariant = Invariant.parse(args[1]);
    EventManager.init(invariant);
    Logger.debug("Invariant: " + invariant);

    cleanupScript = args[2];

    // Maybe load the failure plan.
    try {
      if (args.length == 4) {
        replay = true;
        EventManager.loadPartitionScenarioFromFile(args[3]);
      }
    } catch (Throwable t) {
      Logger.fatal("Failed to initialize the engine.", t);
      System.exit(1);
    }
  }

  /**
   * Print the usage.
   */
  private static void printUsage() {
    Logger.info("Usage:");
    Logger.info("  $ java cofi.faultinjection.Engine <run-script> " +
            "<invariant-string> <cleanup-script> [failure-scenario].");
  }

  /**
   * Read the client-reported event from the specified input stream and handle
   * it accordingly.
   * @param in The input stream to read in the event.
   * @param out The output stream to respond to the client.
   * @throws IOException If failed to read the event or failed to send back the
   *                     response.
   */
  private static void handleEvent(DataInputStream in, DataOutputStream out)
          throws IOException {
    String content = in.readUTF();
    boolean shouldProceed = EventManager.parseAndHandleEvent(content);
    if (!shouldProceed) {
      String failMsgStr = "Failing message: " + content;
      Logger.info(failMsgStr);
      testRunner.scriptOutput.append(failMsgStr).append("\n");
    }
    out.writeBoolean(shouldProceed);
  }

  public static boolean isReplaying() {
    return replay;
  }

  public static TestRunner getTestRunner() {
    return testRunner;
  }

  public static Invariant getInvariant() {
    return invariant;
  }

  public static int getIteration() {
    return iteration;
  }
}
