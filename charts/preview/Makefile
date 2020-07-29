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
	envsubst < values.tmpl.yaml > values.yaml
	$(SED) -e "s/version:.*/version: $(PREVIEW_VERSION)/" Chart.yaml
	jx step helm build --verbose
