OS := $(shell uname)

ifeq ($(OS),Darwin)
SED=sed -i ''
else ifeq ($(OS),Linux)
SED=sed -i
else
	echo "Platform $(OS) not supported"
	exit -1
endif
preview:
	$(SED) -e "s/version:.*/version: $(PREVIEW_VERSION)/" Chart.yaml
	$(SED) -e "s/version:.*/version: $(PREVIEW_VERSION)/" ../*/Chart.yaml
	$(SED) -e "s|repository:.*|repository: $(DOCKER_REGISTRY)\/$(ORG)\/nuxeo-ai|" values.yaml
	$(SED) -e "s/tag:.*/tag: $(PREVIEW_VERSION)/" values.yaml
	echo "  version: $(PREVIEW_VERSION)" >> requirements.yaml
	jx step helm build
