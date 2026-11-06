.PHONY: docker db-up db-down db-reset build lint format test ci run run-orders run-payments stop clean clean-all

-include .env
export

ifneq ($(wildcard $(HOME)/.colima),)
export DOCKER_HOST := unix://$(HOME)/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE := /var/run/docker.sock
endif

docker:
	@colima status >/dev/null 2>&1 || colima start

db-up: docker
	docker compose up -d --wait

db-down:
	docker compose down

db-reset:
	docker compose down -v

build:
	./gradlew assemble

lint:
	./gradlew spotlessCheck checkstyleMain checkstyleTest

format:
	./gradlew spotlessApply

test: docker
	./gradlew test

ci: build lint test

run-orders: db-up
	./gradlew :orders-service:bootRun --no-daemon

run-payments: db-up
	./gradlew :payments-service:bootRun --no-daemon

run: db-up
	$(MAKE) -j2 run-orders run-payments

stop:
	@for port in 8080 8081; do \
		pid=$$(lsof -tiTCP:$$port -sTCP:LISTEN); \
		if [ -n "$$pid" ]; then kill $$pid && echo "stopped $$port ($$pid)"; else echo "nothing on $$port"; fi; \
	done

clean:
	./gradlew clean
	./gradlew --stop
	rm -rf .gradle

clean-all: clean
	rm -rf $(HOME)/.gradle/caches
	docker system prune
