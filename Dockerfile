# Currently, CoFI runs on Ubuntu 16.04.
FROM ubuntu:16.04

WORKDIR /cofi

COPY . .

RUN \
	# Install the dependencies
	apt-get update && \
	apt-get install -y \
		build-essential openjdk-8-jdk maven python-dev python-pip \
		python-virtualenv vim && \
	apt-get dist-upgrade -y && \
	# Config vim
	cp .vimrc /root/ && \
	# Build CoFI
	mvn package && \
	# Set JAVA_HOME
	echo "export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64" >> ~/.bashrc && \
	echo "export PATH=$JAVA_HOME/bin:$PATH" >> ~/.bashrc
