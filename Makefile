.PHONY: setup pc fmt redis-up redis-down redis-reset redis-logs redis-ping redis-cli up down logs

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f

setup:
	bash scripts/setup-precommit.sh

pc:
	pre-commit run --all-files

fmt:
	./mvnw -q -DskipTests spotless:apply

redis-up:
	docker compose -f src/main/java/com/sam/be/infrastructure/cache/images/docker-compose.yaml up -d

redis-down:
	docker compose -f src/main/java/com/sam/be/infrastructure/cache/images/docker-compose.yaml down

redis-reset:
	docker compose -f src/main/java/com/sam/be/infrastructure/cache/images/docker-compose.yaml down -v

redis-logs:
	docker compose -f src/main/java/com/sam/be/infrastructure/cache/images/docker-compose.yaml logs -f redis

redis-ping:
	docker exec -it sam-redis redis-cli ping

redis-cli:
	docker exec -it sam-redis redis-cli
