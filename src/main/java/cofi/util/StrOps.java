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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrOps {
	/**
	 * Check if the given string represents a "get by key" operation,
	 * i.e., "get(\d)".
	 */
	public static boolean isGetByKey(String str) {
		Pattern pGetByKey = Pattern.compile("get\\(\\d*\\)");
		Matcher mGetByKey = pGetByKey.matcher(str);
		return mGetByKey.matches();
	}

	/**
	 * Return the first number in the string. Return null if the given string does
	 * not have a number.
	 */
	public static Integer extractFirstNumber(String str) {
		Pattern pNumber = Pattern.compile("get\\((\\d*)\\)");
		Matcher mNumber = pNumber.matcher(str);
		if (mNumber.find()) {
			return Integer.parseInt(mNumber.group(1));
		} else {
			return null;
		}
	}

	/**
	 * Get the thread ID from the given thread GUID. A thread GUID is of the form:
	 * td####_nd####
	 * @param threadGUID A given thread GUID.
	 * @return The thread ID.
	 */
	public static String getThreadIDFromGUID(String threadGUID) {
		int tidEndIdx = threadGUID.indexOf("_");
		return threadGUID.substring(0, tidEndIdx);
	}

	/**
	 * Get the node ID from the given thread GUID. A thread GUID is of the form:
	 * td####_nd####
	 * @param threadGUID A given thread GUID.
	 * @return The node ID.
	 */
	public static String getNodeIDFromGUID(String threadGUID) {
		int nidStartIdx = threadGUID.indexOf('n');
		return threadGUID.substring(nidStartIdx);
	}

	/**
	 * Get the node ID from the given profiling log name.
	 * @param logName The name of a profiling log. It is in the form of
	 *                td###_nd###.txt
	 * @return The node ID in the log name. It is in the form of nd###.
	 */
	public static String getNodeIDFromProfLogName(String logName) {
		int underlineIndex = logName.indexOf('_');
		return logName.substring(underlineIndex + 1, logName.length() - 4);
	}

	/**
	 * Remove the "/", if exists, at the end of the given pathname.
	 * @param pathName The pathname work on.
	 * @return A pathname without a "/" at the end.
	 */
	public static String rmTailSlash(String pathName) {
		int length = pathName.length();
		if (pathName.charAt(length - 1) == '/') {
			return pathName.substring(0, length - 1);
		} else {
			return pathName;
		}
	}

	/**
	 * Remove the leading node ID, if exists, from given variable name. A node ID
	 * is of the form "nd####" where #### can be a positive number, zero, or a
	 * negative number.
	 * @param varName The variable name to work on.
	 * @return A variable name without node ID at the beginning.
	 */
	public static String rmNodeID(String varName) {
	  Pattern nodeIDPattern = Pattern.compile("^nd-?\\d+.*");
	  Matcher matcher = nodeIDPattern.matcher(varName);
	  if (matcher.find()) {
	  	int realVarNameStartIndex = varName.indexOf('-', 3) + 1;
	  	return varName.substring(realVarNameStartIndex);
		}
	  return varName;
	}

	/**
	 * Return the leading node ID, if exists, from given variable name. A node ID
	 * is of the form "nd####" where #### can be a positive number, zero, or a
	 * negative number. A variable name can be in the form "nd#####-varName". If
	 * the given variable name does not have a node ID, return null.
	 * @param varName The variable name to work on.
	 * @return The node ID if exists. Otherwise, return null.
	 */
	public static String getNodeID(String varName) {
		Pattern nodeIDPattern = Pattern.compile("^nd-?\\d+.*");
		Matcher matcher = nodeIDPattern.matcher(varName);
		if (matcher.find()) {
			int nodeIDEndIndex = varName.indexOf('-', 3);
			return varName.substring(0, nodeIDEndIndex);
		}
		return null;
	}

	/**
	 * Remove the surrounding double quotes, if exist, at the beginning and at the
	 * end of the given string.
	 * @param string The string to work on.
	 * @return A string without double quotes at the beginning and at the end of
	 * the given string.
	 */
	public static String rmDoubleQuotes(String string) {
		int len = string.length();
	  if (len > 1 && string.charAt(0) == '\"' && string.charAt(len - 1) == '\"') {
	  	return string.substring(1, len - 1);
		}
	  return string;
	}
}
