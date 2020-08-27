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

# Check argument count
if [ "$#" -ne 1 ]; then
	echo "usage: "$0" <class-roots-file>"
	exit
fi

curScriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cofiJar="$curScriptDir"/../target/cofi-0.1-SNAPSHOT-jar-with-dependencies.jar
classRootsFile="$1"

while read -r classRoot 
do
  # Change the working directory to the class root so that we can directly 
  # overwrite the class files.
	cd "$classRoot" && \
  currentDir="$( pwd )"
  echo "Working on directory $currentDir"
  # Add the getters.
  java -cp ".:$cofiJar" cofi.instrumentation.GetterAdder && \
  # Change back to the original directory.
  cd -
done < "$classRootsFile"