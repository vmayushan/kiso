#!/bin/sh
set -e

cp -a /app/. /${DOCKER_SANDBOX_WORKDIR}/
chown -R ${DOCKER_SANDBOX_USER} /${DOCKER_SANDBOX_WORKDIR}

exec "$@"