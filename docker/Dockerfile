# Base image
ARG PLATFORM_VERSION=2023
FROM docker-private.packages.nuxeo.com/nuxeo/nuxeo:${PLATFORM_VERSION}

# Build arguments
ARG BUILD_TAG=unknown
ARG SCM_REF=unknown
ARG VERSION=unknown
ARG PACKAGES_AUTH_USER
ARG PACKAGES_AUTH_TOKEN
ARG PLATFORM_VERSION

# Labels
LABEL org.nuxeo.ai.build-tag="$BUILD_TAG" \
      org.nuxeo.ai.scm-ref="$SCM_REF" \
      org.nuxeo.ai.version="$VERSION" \
      org.opencontainers.image.version="$VERSION" \
      org.opencontainers.image.revision="$SCM_REF" \
      org.opencontainers.image.source="git@github.com:nuxeo/nuxeo-ai.git" \
      org.opencontainers.image.title="Nuxeo AI Core" \
      org.opencontainers.image.description="Nuxeo AI Core based on Nuxeo Platform $PLATFORM_VERSION"

# Switch to root
USER root

# Combined system dependencies and secure private repo installation into a single RUN to reduce image layers
# --- Install system dependencies and secure private repo ---
RUN --mount=type=secret,id=nuxeo-private,target=/tmp/nuxeo-private.repo \
    cp /tmp/nuxeo-private.repo /etc/yum.repos.d/nuxeo-private.repo && \
    dnf -y install --skip-broken \
        ghostscript \
        libwpd-tools \
        perl-Archive-Zip \
        perl-Image-ExifTool \
        poppler-utils \
        libreoffice-calc \
        libreoffice-headless \
        libreoffice-impress \
        libreoffice-writer \
        gettext \
        curl \
        unzip \
        zip \
        ffmpeg-nuxeo \
        ImageMagick \
        ccextractor && \
    rm -f /etc/yum.repos.d/nuxeo-private.repo && \
    dnf clean all && \
    rm -rf /var/cache/dnf

# Create /packages directory
RUN mkdir -p /packages && chown 900:0 /packages

# Copy Nuxeo AI packages into /packages
COPY --chown=900:0 nuxeo-ai-core-${VERSION}.zip /packages/
COPY --chown=900:0 nuxeo-ai-aws-package-${VERSION}.zip /packages/
COPY --chown=900:0 nuxeo-ai-gcp-package-${VERSION}.zip /packages/
COPY --chown=900:0 nuxeo-ai-image-quality-package-${VERSION}.zip /packages/

RUN /install-packages.sh --offline \
    /packages/nuxeo-ai-core-${VERSION}.zip \
    /packages/nuxeo-ai-aws-package-${VERSION}.zip \
    /packages/nuxeo-ai-gcp-package-${VERSION}.zip \
    /packages/nuxeo-ai-image-quality-package-${VERSION}.zip

# Switch back to Nuxeo user
USER 900
