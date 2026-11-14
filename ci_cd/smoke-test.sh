#!/usr/bin/env bash
set -euo pipefail

IMAGE="$1"
NET="smoke-${BUILD_NUMBER:-local}"

cleanup() {
  docker rm -f "$NET-app" "$NET-db" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "$NET"

docker run -d --name "$NET-db" --network "$NET" \
  -e POSTGRES_DB=eventpro -e POSTGRES_USER=eventpro -e POSTGRES_PASSWORD=smoke \
  postgres:17-alpine

for i in $(seq 1 30); do
  docker exec "$NET-db" pg_isready -U eventpro -d eventpro && break
  sleep 2
done

docker run -d --name "$NET-app" --network "$NET" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="jdbc:postgresql://$NET-db:5432/eventpro" \
  -e DB_USERNAME=eventpro -e DB_PASSWORD=smoke \
  -e ADMIN_NAME=smoke -e ADMIN_EMAIL=smoke@test.local -e ADMIN_PASSWORD=smoke-password-1 \
  "$IMAGE"

for i in $(seq 1 40); do
  if docker exec "$NET-app" wget -q -O- http://127.0.0.1:8080/actuator/health | grep -q '"UP"'; then
    echo "Smoke test OK : l'application répond UP"
    exit 0
  fi
  sleep 3
done

echo "Smoke test ÉCHEC : l'application n'est pas UP. Dernières lignes des logs :"
docker logs --tail 50 "$NET-app"
exit 1