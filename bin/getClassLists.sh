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

classRoots=(\
	"/app/hadoop-cloud-storage-project/hadoop-cos/target/classes" \
	"/app/hadoop-common-project/hadoop-annotations/target/classes" \
	"/app/hadoop-common-project/hadoop-auth/target/classes" \
	"/app/hadoop-common-project/hadoop-common/target/classes" \
	"/app/hadoop-common-project/hadoop-kms/target/classes" \
	"/app/hadoop-common-project/hadoop-minikdc/target/classes" \
	"/app/hadoop-common-project/hadoop-nfs/target/classes" \
	"/app/hadoop-common-project/hadoop-registry/target/classes" \
	"/app/hadoop-hdfs-project/hadoop-hdfs/target/classes" \
	"/app/hadoop-hdfs-project/hadoop-hdfs-client/target/classes" \
	"/app/hadoop-hdfs-project/hadoop-hdfs-httpfs/target/classes" \
	"/app/hadoop-hdfs-project/hadoop-hdfs-nfs/target/classes" \
	"/app/hadoop-hdfs-project/hadoop-hdfs-rbf/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-app/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-common/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-core/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-hs/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-hs-plugins/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-jobclient/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-nativetask/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-shuffle/target/classes" \
	"/app/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-uploader/target/classes" \
	"/app/hadoop-maven-plugins/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-api/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-applications/hadoop-yarn-applications-catalog/hadoop-yarn-applications-catalog-webapp/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-applications/hadoop-yarn-applications-distributedshell/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-applications/hadoop-yarn-applications-mawo/hadoop-yarn-applications-mawo-core/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-applications/hadoop-yarn-applications-unmanaged-am-launcher/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-applications/hadoop-yarn-services/hadoop-yarn-services-api/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-applications/hadoop-yarn-services/hadoop-yarn-services-core/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-client/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-common/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-csi/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-applicationhistoryservice/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-common/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-nodemanager/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-resourcemanager/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-router/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-sharedcachemanager/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-timeline-pluginstorage/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-timelineservice/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-timelineservice-documentstore/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-timelineservice-hbase/hadoop-yarn-server-timelineservice-hbase-client/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-timelineservice-hbase/hadoop-yarn-server-timelineservice-hbase-common/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-timelineservice-hbase/hadoop-yarn-server-timelineservice-hbase-server/hadoop-yarn-server-timelineservice-hbase-server-1/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-timelineservice-hbase/hadoop-yarn-server-timelineservice-hbase-server/hadoop-yarn-server-timelineservice-hbase-server-2/target/classes" \
	"/app/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-server/hadoop-yarn-server-web-proxy/target/classes" \
)

touch /app/rpc-methods.txt
for classRoot in "${classRoots[@]}"; do
	/ycz/bin/findStaticFields.sh "$classRoot" /app/rpc-methods.txt /app/rpc-methods.txt 1 2
done
