# Build the image on CoFI's image
FROM hanseychen/cofi:0.1

WORKDIR /app

COPY . .

RUN \
	# Install the dependencies.
	apt-get install -y ant git && \
	easy_install pyYaml && \
	easy_install six && \
	pip install psutil && \
	# Install ccm for easy setup of Cassandra clusters
	git clone https://github.com/riptano/ccm.git && \
	cd ccm && \
	./setup.py install && \
	cd /app && \
	# Build Cassandra.
	ant build && \
	# Instrument Cassandra.
	/cofi/bin/add-getters.sh /app/class-roots.txt && \
	# Package Cassandra.
	ant
