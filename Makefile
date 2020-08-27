run: build-image run-container

build-image:
	docker image build -t hanseychen/cofi:0.1 .

run-container:
	docker container run -it --name cofi hanseychen/cofi:0.1

clean: rm-container rm-image

rm-container:
	docker container rm --force cofi

rm-image:
	docker image rm hanseychen/cofi:0.1
