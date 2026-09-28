.PHONY: setup pc fmt up down logs infra-up infra-down backend-up redis-up redis-down redis-reset redis-logs redis-ping redis-cli

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f

infra-up:
	docker compose up -d postgres redis

infra-down:
	docker compose stop postgres redis

backend-up:
	docker compose up -d --build backend

setup:
	bash scripts/setup-precommit.sh

pc:
	pre-commit run --all-files

fmt:
	./mvnw -q -DskipTests spotless:apply

redis-up:
	docker compose up -d redis

redis-down:
	docker compose stop redis

redis-reset:
	docker compose rm -f -s -v redis

redis-logs:
	docker compose logs -f redis

redis-ping:
	docker exec -it sam-redis redis-cli ping

redis-cli:
	docker exec -it sam-redis redis-cli
