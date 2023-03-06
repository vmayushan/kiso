FROM alpine:latest

ENV STRESS_VERSION=1.0.4
COPY stress-$STRESS_VERSION.tar.gz .

RUN apk add --update bash g++ make curl && \
  tar xzvf stress-$STRESS_VERSION.tar.gz && \
  rm stress-$STRESS_VERSION.tar.gz && \
  cd stress-${STRESS_VERSION} && \
  ./configure && make && make install && \
  apk del g++ make curl && \
  rm -rf /tmp/* /var/tmp/* /var/cache/apk/* /var/cache/distfiles/*

WORKDIR /app

ARG DOCKER_SANDBOX_USER
ENV DOCKER_SANDBOX_USER=${DOCKER_SANDBOX_USER}

ARG DOCKER_SANDBOX_WORKDIR
ENV DOCKER_SANDBOX_WORKDIR=${DOCKER_SANDBOX_WORKDIR}

RUN adduser \
  -D \
  -h /${DOCKER_SANDBOX_WORKDIR} \
  -g '' ${DOCKER_SANDBOX_USER}

WORKDIR /${DOCKER_SANDBOX_WORKDIR}

CMD tail -f /dev/null
COPY docker-entrypoint.sh /usr/local/bin/
ENTRYPOINT ["docker-entrypoint.sh"]