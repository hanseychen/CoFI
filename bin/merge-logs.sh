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

curScriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cofiRoot="$curScriptDir"/..
cofiJar="$cofiRoot"/target/cofi-0.1-SNAPSHOT-jar-with-dependencies.jar
daikonJar="$cofiRoot"/lib/daikon.jar

cd "$cofiRoot"

# Prepare the necessary directories to store the output.
mkdir -p tmpLogs
rm -rf tmpLogs/*
mkdir -p dtraces
rm -rf dtraces/*
mkdir -p inv-gzs
rm -rf inv-gzs/*

# Merge profile logs to generate dtrace files.
java -Xmx6g -cp .:$cofiJar cofi.ParisRunner merging $1

# Run Daikon on dtrace files.
echo "" > invariants.txt
for dtrace in dtraces/*.dtrace; do
	echo "[runDaikon] Mining $dtrace"
	java -Xmx10g -XX:-UseGCOverheadLimit -cp $daikonJar daikon.Daikon $dtrace >> invariants.txt
done

# Clean-up the zipped invariants.
mv *.inv.gz inv-gzs/

cd -
