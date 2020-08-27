#!/usr/bin/python
# Copyright 2020 Haicheng Chen
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import sys

# Check arguments
if len(sys.argv) < 4:
    print("usage:\n$ {} <script-to-run> <cleanup-script> <failure-plan>"
            .format(sys.argv[0]))
    exit()

# Initialize the pathnames
scriptPath = os.path.dirname(os.path.realpath(__file__))
yczJarPath = scriptPath + "/../target/yCozy-0.1-SNAPSHOT.jar"
runScriptPath = sys.argv[1]
cleanupScriptPath = sys.argv[2]
failurePlanFileName = os.getcwd() + "/" + sys.argv[3]

# Open the failure plan file to read
failurePlanFile = open(failurePlanFileName, "r")
if failurePlanFile.mode != 'r':
    print("Failed to open {} for read.".format(failurePlanFileName))
    exit()

# Load the invariant from the 2nd line in the file
failurePlanFile.readline()
invString = failurePlanFile.readline().strip()

# Start the test with the specified failure plan
startEngineCmd = ("java -Xmx6g -cp .:{} "
                  "cofi.faultinjection.Engine \"{}\" \"{}\" \"{}\" \"{}\"").format(
                      yczJarPath, runScriptPath, invString, cleanupScriptPath,
                      failurePlanFileName)
print(startEngineCmd)
os.system(startEngineCmd)
