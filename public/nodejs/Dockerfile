FROM alpine:3.19 AS worker

FROM saschpe/android-ndk:37.0-jdk23.0.2_7-ndk30.0.14904198-cmake4.1.2

USER root

ENV LIBNODE_PATH=/opt/libnode

# Install nodejs 24.5.0 (same version as nodejs-mobile)
ARG NODE_MAJOR=24

RUN apt-get update
RUN apt-get install -y ca-certificates curl gnupg make
RUN mkdir -p /etc/apt/keyrings
RUN curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
RUN echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_$NODE_MAJOR.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list
RUN apt-get update
RUN apt-get install nodejs -y

COPY node_modules/capacitor-nodejs/android/libnode /opt/libnode

WORKDIR /app