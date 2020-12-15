ARG PLATFORM_VERSION
FROM docker-private.packages.nuxeo.com/nuxeo/nuxeo:${PLATFORM_VERSION}

USER 0
#RUN yum -y install https://mirrors.rpmfusion.org/free/el/rpmfusion-free-release-7.noarch.rpm && \
#    yum -y install ffmpeg
COPY nuxeo-private.repo~gen /etc/yum.repos.d/nuxeo-private.repo
RUN yum -y update && \
        yum -y install epel-release deltarpm && \
        yum -y --setopt=skip_missing_names_on_install=False install \
            ghostscript \
            ImageMagick \
            libreoffice-calc libreoffice-headless libreoffice-impress libreoffice-writer \
            libwpd-tools \
            perl-Archive-Zip \
            perl-Image-ExifTool \
            poppler-utils \
            ufraw \
        ccextractor \
        ffmpeg-nuxeo && \
    rm /etc/yum.repos.d/nuxeo-private.repo

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

COPY --chown=$NUXEO_USER log4j2.xml /opt/nuxeo/server/lib/log4j2.xml
COPY *.zip /tmp/
RUN /install-packages.sh /tmp/nuxeo-ai-core.zip /tmp/nuxeo-ai-image-quality-package.zip /tmp/nuxeo-web-ui.zip /tmp/nuxeo-csv.zip
