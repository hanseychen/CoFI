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

curDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
yczJar=$curDir/../target/yCozy-0.1-SNAPSHOT.jar
sootJar=$curDir/../lib/sootclasses.jar

function getClassInRoot {
	classRoot="$1"

	echo "Getting classes in $classRoot"
	echo "/app/$classRoot/target/classes" > /app/tmpClassRoot.txt
	java -Xmx4g -cp .:$yczJar:$sootJar ycz.analysis.ClassGetter /app/tmpClassRoot.txt "/app/$classRoot"
}

while read classRoot; do
	getClassInRoot "$classRoot"
done <"$1"
