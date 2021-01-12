ARG PLATFORM_VERSION
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

COPY nuxeo-ai-core-*.zip /tmp/nuxeo-ai-core.zip
COPY nuxeo-web-ui.zip /tmp/nuxeo-web-ui.zip
COPY nuxeo-csv.zip /tmp/nuxeo-csv.zip
RUN /install-packages.sh /tmp/nuxeo-ai-core.zip /tmp/nuxeo-web-ui.zip /tmp/nuxeo-csv.zip
