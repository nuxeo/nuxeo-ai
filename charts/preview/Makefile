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
	mv values.yaml values.yaml.tmp
	envsubst < values.yaml.tmp > values.yaml
	$(SED) -e "s/version:.*/version: $(PREVIEW_VERSION)/" Chart.yaml
	echo "  version: $(PREVIEW_VERSION)" >> requirements.yaml
	jx step helm build
