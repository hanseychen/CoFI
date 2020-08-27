#!/bin/bash

function assertNoException {
	# Get exceptions from the log.
	exceptionLines="$( grep "Exception" ~/.ccm/test/"$1"/logs/system.log )"

	# Get exceptions that are always benign.
	exceptionLines="$( echo "$exceptionLines" | grep -v "IncomingTcpConnection.java:103 - UnknownColumnFamilyException reading from socket; closing" )"
	exceptionLines="$( echo "$exceptionLines" | grep -v "this is likely due to the schema not being fully propagated" )"

	# Check if we still have exception logs.
	exceptionCnt="$( echo "$exceptionLines" | grep "Exception" | wc -l )"

	if [ "$exceptionCnt" -ne 0 ]; then
		error "Found exceptions in $1's log:"
		echo "$exceptionLines"
		failTest
	fi
}
