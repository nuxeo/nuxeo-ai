ARG PLATFORM_VERSION
FROM docker-private.packages.nuxeo.com/nuxeo/nuxeo:${PLATFORM_VERSION}

USER 0
#RUN yum -y install https://mirrors.rpmfusion.org/free/el/rpmfusion-free-release-7.noarch.rpm && \
#    yum -y install ffmpeg

# Build arguments for private repo authentication
ARG PACKAGES_AUTH_USER
ARG PACKAGES_AUTH_TOKEN

COPY nuxeo-private.repo /etc/yum.repos.d/nuxeo-private.repo

ENV PACKAGES_AUTH_USER=${PACKAGES_AUTH_USER}
ENV PACKAGES_AUTH_TOKEN=${PACKAGES_AUTH_TOKEN}
RUN envsubst < /etc/yum.repos.d/nuxeo-private.repo > /etc/yum.repos.d/nuxeo-private.repo.subst && \
    mv /etc/yum.repos.d/nuxeo-private.repo.subst /etc/yum.repos.d/nuxeo-private.repo

# Add YUM credentials for private repo access, run dnf, then clean up credentials
RUN echo "PACKAGES_AUTH_USER=${PACKAGES_AUTH_USER}" && \
    echo "PACKAGES_AUTH_TOKEN=${PACKAGES_AUTH_TOKEN}" && \
    echo "--- nuxeo-private.repo ---" && cat /etc/yum.repos.d/nuxeo-private.repo && \
    dnf repolist && \
    dnf -y update || cat /var/log/dnf.log && \
    dnf -y install epel-release deltarpm || cat /var/log/dnf.log && \
    dnf -y --setopt=skip_missing_names_on_install=False install \
        ghostscript \
        ImageMagick \
        libreoffice-calc libreoffice-headless libreoffice-impress libreoffice-writer \
        libwpd-tools \
        perl-Archive-Zip \
        perl-Image-ExifTool \
        poppler-utils \
        ufraw \
        ccextractor \
        ffmpeg-nuxeo || cat /var/log/dnf.log && \
    dnf clean all && \
    rm -rf /var/cache/dnf && \
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
