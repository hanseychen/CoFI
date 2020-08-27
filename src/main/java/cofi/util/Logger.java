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

import java.io.IOException;
import java.io.PrintWriter;

public class Logger {
	// FIXME: Make it configurable.
	private static final boolean log2File = false;
	private static PrintWriter out;
	private static boolean debug = true;

	/****************************************
	 * Create the log file for current run. *
	 ****************************************/
	static {
		try {
			long millis = System.currentTimeMillis();
			if (log2File) {
				out = new PrintWriter(millis + ".log", "UTF-8");
			} else {
				out = new PrintWriter(System.out);
			}
		} catch (IOException ioe) {
			System.err.println("Failed to create log file:" + ioe);
			System.exit(1);
		}
	}

	/***************************
	 * Manipulating log level. *
	 ***************************/
	public static void enableDebugLog() { debug = true; }
	public static void disableDebugLog() { debug = false; }

	private static void log(String msg) {
		out.print(msg + "\n");
		out.flush();
	}

	private static void log(String msg, Throwable t) {
		out.print(msg + "\n");
		t.printStackTrace(out);
		out.flush();
	}

	/********************************
	 * Logging at different levels. *
	 ********************************/
	public static void cross() {
		out.print("x");
		out.flush();
	}
	public static void dot() {
		out.print(".");
		out.flush();
	}
	public static void debug(String msg) { if (debug) log("[debug] " + msg); }
	public static void debug(String msg, Throwable t) {
		if (debug) log("[debug] " + msg, t);
	}

	public static void info(String msg) {  log("[info]  " + msg); }
	public static void info(String msg, Throwable t) {
		log("[info]  " + msg, t);
	}

	public static void warn(String msg) {  log("[warn]  " + msg); }
	public static void warn(String msg, Throwable t) {
		log("[warn]  " + msg, t);
	}

	public static void error(String msg) { log("[error] " + msg); }
	public static void error(String msg, Throwable t) {
		log("[error] " + msg, t);
	}

	public static void fatal(String msg) { log("[fatal] " + msg); }
	public static void fatal(String msg, Throwable t) {
		log("[fatal] " + msg, t);
	}
}
