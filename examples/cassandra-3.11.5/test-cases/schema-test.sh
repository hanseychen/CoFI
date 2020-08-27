#!/bin/bash

# Info level log.
function info {
	printf "[info]  " 
	printf "$@"
	printf "\n"
}

# Error level log.
function error {
	printf "[error] " 
	printf "$@"
	printf "\n"
}

# Fail the test.
function failTest {
	error "test has failed"
	exit 0
}

# Run a cql statement
function runCql {
	"$cassandraRoot"/bin/cqlsh --exec="$1" --tty 2>&1
}

# Assert that the given return code and output either represents a success or
# "schema version mismatch".
function assertPassOrMismatch {
	ret="$1"
	out="$2"
	if [ "$ret" -eq 0 ]; then
		info "Succeeded."
	elif [ "$ret" -eq 2 ] && [[ "$out" == *"schema version mismatch detected"* ]]; then
		info "Succeeded with version mismatch."
	else
		error "Failed."
		echo "$out"
		assertNoBadFailure
		failTest
	fi
}

# Assert that the given return code represents a success.
function assertPass {
	ret="$1"
	out="$2"
	if [ "$ret" -ne 0 ]; then
		error "Failed."
		echo "$out"
		assertNoBadFailure
		failTest
	fi
}

# Assert that the given output contains the given string
function assertContains {
	out="$1"
	str="$2"
	if [[ ! "$out" =~ "$str" ]]; then
		error "Invalid output."
		echo "$out"
		echo "does not contain"
		echo "$str"
		assertNoBadFailure
		failTest
	fi
}

# Assert that the specified node does not have logs in the specified level.
function assertNodeNoLogAtLevel {
	logContent="$( grep "$2" ~/.ccm/test/"$1"/logs/system.log )"
	logCount="$( grep "$2" ~/.ccm/test/"$1"/logs/system.log | wc -l )"
	if [ "$logCount" -ne "0" ]; then
		error "$1 has log at level $2"
		echo "$logContent"
		error "test has failed:"
		exit 0
	fi
}

function assertNodeNoBadFailure {
	assertNodeNoLogAtLevel "$1" ERROR
	assertNodeNoLogAtLevel "$1" FATAL 
	assertNodeNoLogAtLevel "$1" Exception
}

function assertNoBadFailure {
	assertNodeNoBadFailure node1
	assertNodeNoBadFailure node2
	assertNodeNoBadFailure node3
}

# Declare some path variables.
cassandraRoot=/app

###################
# Start the test. #
###################

# Start the cluster
ccm create test --install-dir="$cassandraRoot" -n 3 -s

# Create the keyspace and the table. If the creation fails for a reason other
# than "schema version mismatch detected", the test has failed.
info "Creating keyspace test_ks."
out="$( runCql "create keyspace test_ks with replication = {'class':'SimpleStrategy','replication_factor':3};" )"
assertPassOrMismatch "$?" "$out"
info "Creating table test_tbl"
out="$( runCql "create table test_ks.test_tbl (row_id text primary key, c1 int, c2 int);" )"
assertPassOrMismatch "$?" "$out"

# Insert some data. This should not fail.
info "Inserting data"
out="$( runCql "insert into test_ks.test_tbl (row_id, c1, c2) values ('row1', 1, 1);" )"
assertPass "$?" "$out"
out="$( runCql "insert into test_ks.test_tbl (row_id, c1, c2) values ('row2', 2, 2);" )"
assertPass "$?" "$out"

# Drop and then re-add a column.
info "Dropping the column."
out="$( runCql "alter table test_ks.test_tbl drop c1;" )"
assertPassOrMismatch "$?" "$out"
info "Re-adding the column."
out="$( runCql "alter table test_ks.test_tbl add c1 int;" )"
assertPassOrMismatch "$?" "$out"

# Add some more data.
info "Inserting data"
out="$( runCql "insert into test_ks.test_tbl (row_id, c1, c2) values ('row3', 3, 3);" )"
assertPass "$?" "$out"

# Query the data and check.
info "Reading back the data"
out="$( runCql "consistency quorum; select * from test_ks.test_tbl;" )"
assertPass "$?" "$out"
assertContains "$out" "row1 | null |  1"
assertContains "$out" "row2 | null |  2"
assertContains "$out" "row3 |    3 |  3"

statusOut="$( ccm status 2>&1 )"
assertContains "$statusOut" "node1: UP"
assertContains "$statusOut" "node2: UP"
assertContains "$statusOut" "node3: UP"

ccm stop

assertNoBadFailure
