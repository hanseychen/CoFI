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
package cofi;

import cofi.mining.LogMerger;
import cofi.mining.InvPruner;
import cofi.util.ErrCode;
import cofi.util.Logger;

/**
 * FIXME: This class is not necessary. We should get rid of it later.
 * This is the main class of Paris. Its main function guides the testing.
 */
public class ParisRunner {
  private static String targetCodeDir;
  private static Phase phase;

  enum Phase { MERGING, PRUNING, UNKNOWN }

  public static void main(String[] args) {
    parseArgs(args);

    switch (phase) {
      case MERGING:
        LogMerger.run(targetCodeDir);
        break;
      default:
    }
  }

  private static void printUsage() {
    Logger.info("Usages:");
    Logger.info("$ java cofi.ParisRunner profiling <class-dir>");
    Logger.info("$ java cofi.ParisRunner tracking <class-dir> <invariant>");
  }

  private static void parseArgs(String[] args) {
    boolean badArg = false;

    // User should provide at least two arguments, a `phase`, and a `class-dir`.
    if (args.length < 2) {
      badArg = true;
    }

    if (!badArg) {
      targetCodeDir = args[1];
      switch (args[0]) {
        case "merging":
          phase = Phase.MERGING;
          break;
        case "prune":
          phase = Phase.PRUNING;
          break;
        default:
          Logger.fatal("Bad verb in the arguments: " + args[0]);
          badArg = true;
      }
    }

    if (badArg) {
      printUsage();
      System.exit(ErrCode.BAD_ARG);
    }
  }
}
