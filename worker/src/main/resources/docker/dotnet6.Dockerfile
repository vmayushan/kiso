FROM mcr.microsoft.com/dotnet/sdk:6.0-alpine
WORKDIR /app

ARG DOCKER_SANDBOX_USER
ENV DOCKER_SANDBOX_USER=${DOCKER_SANDBOX_USER}

ARG DOCKER_SANDBOX_WORKDIR
ENV DOCKER_SANDBOX_WORKDIR=${DOCKER_SANDBOX_WORKDIR}

RUN adduser \
  --disabled-password \
  --home /${DOCKER_SANDBOX_WORKDIR} \
  --gecos '' ${DOCKER_SANDBOX_USER}

RUN dotnet new console -lang c# -f net6.0
RUN dotnet run
RUN rm Program.cs

WORKDIR /${DOCKER_SANDBOX_WORKDIR}

CMD tail -f /dev/null
COPY docker-entrypoint.sh /usr/local/bin/
ENTRYPOINT ["docker-entrypoint.sh"]