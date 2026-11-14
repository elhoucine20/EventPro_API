FROM jenkins/jenkins:lts-jdk21

USER root
# Client Docker uniquement (pas le démon), copié depuis l'image officielle
COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker
#Plugin buildx : active BuildKit pour "docker build"
COPY --from=docker/buildx-bin:latest /buildx /usr/libexec/docker/cli-plugins/docker-buildx
USER jenkins