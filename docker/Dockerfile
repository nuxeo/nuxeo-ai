ARG PLATFORM_VERSION
FROM docker-private.packages.nuxeo.com/nuxeo/nuxeo:${PLATFORM_VERSION}

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

#USER 0
#RUN curl -f -Lo /install-packages.sh https://raw.githubusercontent.com/nuxeo/nuxeo/master/docker/slim/install-packages.sh && \
#  chmod a+rx /install-packages.sh
#
#USER $NUXEO_USER
COPY nuxeo-ai-core-*.zip /tmp/nuxeo-ai-core.zip
#COPY $NUXEO_CONF $NUXEO_HOME/bin/nuxeo.conf
#COPY /etc/nuxeo/nuxeo.conf.template $NUXEO_HOME/bin/nuxeo.conf
#RUN /install-packages.sh --clid ${CLID} /tmp/nuxeo-ai-core.zip
