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

import sys
import os

# Check arguments
if len(sys.argv) < 3:
    print("Usage:\n$ {} <script-to-run> <cleanup-script>".format(sys.argv[0]))
    exit()

# Get the correct paths for the needed files.
scriptPath = os.path.dirname(os.path.realpath(__file__))
invFilePath = scriptPath + "/../selected-invariants.txt"
inv2TestPath = scriptPath + "/../invariant-to-test.txt"
cofiJarPath = scriptPath + "/../target/cofi-0.1-SNAPSHOT-jar-with-dependencies.jar"
runScriptPath = sys.argv[1]
cleanupScriptPath = sys.argv[2]

# Tell CoFI to run in the fault injection stage.
os.system("echo \"INJECTION\" > /cofi/stage.txt")

# Open the invariant file for read.
invFile = open(invFilePath, "r")
if invFile.mode != 'r':
    print("Failed to open {} for read.".format(invFilePath))
    exit()

# Read the invariants one by one. Each invariant will go through the testing
# phase separately.
startEngineCmd = "java -Xmx6g -cp .:{} cofi.faultinjection.Engine \"{}\" \"{}\" \"{}\""
for lineNumber, lineContent in enumerate(invFile):
    # Let CoFI know which invariant to use.
    lineContent = lineContent.strip()
    os.system("echo \"{}\" > {}".format(lineContent, inv2TestPath))

    # Start the test engine to inject faults. The engine will start the run 
    # script.
    os.system(startEngineCmd.format(cofiJarPath, runScriptPath, lineContent, cleanupScriptPath))
