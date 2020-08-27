help:
	echo "Please take a look at the Makefile"

build-image:
	docker image build -t cofi:cassandra-3 -f Dockerfile .

run-container:
	docker container run -it --privileged --rm --name $(container_name) $(image_name)
