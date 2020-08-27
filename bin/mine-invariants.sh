#!/bin/bash
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

# Check arguments
if [[ "$#" -ne 3 ]]; then
	echo "usage: "$0" <interesting-variables-file> <test-file> <clean-up-file>"
	exit 1
fi

curScriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cofiRoot="$curScriptDir"/..
cofiJar="$cofiRoot"/target/cofi-0.1-SNAPSHOT-jar-with-dependencies.jar
varFile="$1"
testFile="$2"
cleanUpFile="$3"

#########
# Prepare
#########
# Copy the interesting varibale file to the right place so that CoFI can load it
cp "$varFile" "$cofiRoot"/interesting-variables.txt
if [[ "$?" -ne 0 ]]; then
	echo "Failed to copy "$varFile" to /cofi/interesting-variables.txt."
	exit 1
fi
# Tell CoFI to run in the invariant mining stage.
echo "MINING" > "$cofiRoot"/stage.txt
# Create the directory to store profiling logs.
mkdir -p "$cofiRoot"/profiling-logs
rm -rf "$cofiRoot"/profiling-logs/*

##############
# Run the test
##############
# Cleanup possible leftovers and run the test to mine invariants.
"$cleanUpFile" && "$testFile"

################################
# Mine invariants from the logs.
################################
"$cofiRoot"/bin/merge-logs.sh "$cofiRoot"/profiling-logs

#######################
# Prune the invariants.
#######################
"$cofiRoot"/bin/prune-invariants.sh "$cofiRoot"/invariants.txt "$cofiRoot"/interesting-variables.txt
