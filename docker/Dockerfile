ARG PLATFORM_VERSION
FROM docker-private.packages.nuxeo.com/nuxeo/nuxeo:${PLATFORM_VERSION} as builder

USER $NUXEO_USER
ARG CLID
# workaround NXCT-548
ARG NUXEO_PACKAGES
COPY *.zip /tmp/
RUN NUXEO_CLID="${CLID}" /docker-entrypoint.sh nuxeoctl mp-list && \
    rm -f $NUXEO_HOME/configured $NUXEO_HOME/configured-hf

FROM docker-private.packages.nuxeo.com/nuxeo/nuxeo:${PLATFORM_VERSION}

USER 0
RUN yum -y localinstall --nogpgcheck https://mirrors.rpmfusion.org/free/el/rpmfusion-free-release-7.noarch.rpm
RUN yum -y install ffmpeg
USER $NUXEO_USER

ARG PLATFORM_VERSION
ARG VERSION
ARG SCM_REF
ARG BUILD_TAG
LABEL org.nuxeo.version="${VERSION}"
LABEL org.nuxeo.scm-ref="${SCM_REF}"
LABEL org.nuxeo.scm-url="git@github.com:nuxeo/nuxeo-ai.git"
LABEL org.nuxeo.build-tag="${BUILD_TAG}"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${SCM_REF}"
LABEL org.opencontainers.image.source="git@github.com:nuxeo/nuxeo-ai.git"
LABEL org.opencontainers.image.title="Nuxeo AI Core"
LABEL org.opencontainers.image.description="Nuxeo AI Core based on Nuxeo Platform ${PLATFORM_VERSION}"
LABEL org.opencontainers.image.authors="Nuxeo AI <ai-team@nuxeo.com>"
LABEL org.opencontainers.image.vendor="Nuxeo"
LABEL org.opencontainers.image.licenses="(C) Copyright 2020 Nuxeo (https://nuxeo.com/)."

RUN rm -rf /opt/nuxeo/server/*
COPY --chown=$NUXEO_USER --from=builder $NUXEO_HOME/ /opt/nuxeo/server
COPY --chown=$NUXEO_USER log4j2.xml /opt/nuxeo/server/lib/log4j2.xml
COPY --chown=$NUXEO_USER --from=builder /etc/nuxeo/nuxeo.conf /etc/nuxeo/nuxeo.conf.build
