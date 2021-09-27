OS := $(shell uname)

ifeq ($(OS),Darwin)
SED=sed -i ''
else ifeq ($(OS),Linux)
SED=sed -i
else
	echo "Platform $(OS) not supported"
	exit -1
endif

init:
	helm init --client-only --stable-repo-url=https://charts.helm.sh/stable
	helm repo add jenkins-x https://jenkins-x-charts.github.io/v2/
	helm repo add jenkins-x-local http://jenkins-x-chartmuseum:8080
	helm repo add chartmuseum.platform.dev.nuxeo.com https://chartmuseum.platform.dev.nuxeo.com
	helm repo add chartmuseum.ai.dev.nuxeo.com https://chartmuseum.ai.dev.nuxeo.com
	helm dependency update .

preview: init
	envsubst < values.tmpl.yaml > values.yaml
	$(SED) -e "s/version:.*/version: $(PREVIEW_VERSION)/" Chart.yaml

clean:
	rm -rf charts requirements.lock
