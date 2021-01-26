/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/).
 * This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 * Notice of copyright on this source code does not indicate publication.
 *
 * Contributors:
 *     Julien Carsique <jcarsique@nuxeo.com>
 */

library "nxAILibUntrusted"

pipeline {
    agent {
        label "jenkins-ai-nuxeo11"
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5'))
        timeout(time: 4, unit: 'HOURS')
    }
    environment {
        ORG = 'nuxeo'
        APP_NAME = 'nuxeo-ai'
        AI_CORE_VERSION = readMavenPom().getVersion()
        INSIGHT_DEMOS_VERSION = readMavenPom().getProperties().getProperty('nuxeo.insight-demos.version')
        WEBUI_VERSION = readMavenPom().getProperties().getProperty('nuxeo.webui.version')
        SCM_REF = "${sh(script: 'git show -s --pretty=format:\'%H%d\'', returnStdout: true).trim()}"
        PREVIEW_NAMESPACE = jx.normalizeNS("$APP_NAME-$BRANCH_NAME")
        PREVIEW_URL = "https://preview-${PREVIEW_NAMESPACE}.ai.dev.nuxeo.com"
        PERSISTENCE = "${BRANCH_NAME ==~ 'master.*'}"
        PACKAGE_PATTERN = 'addons/*-package/target/nuxeo*package*.zip *-package/target/nuxeo-ai-core-*.zip'
        MARKETPLACE_URL = 'https://connect.nuxeo.com/nuxeo/site/marketplace'
        MARKETPLACE_URL_PREPROD = 'https://nos-preprod-connect.nuxeocloud.com/nuxeo/site/marketplace'
    }
    stages {
        stage('Init') {
            steps {
                container('platform11') {
                    script {
                        stepsInit(true) {
                            // FIXME INSIGHT-861, INSIGHT-937, INSIGHT-1002
                            sh """
# skaffold
curl -f -Lo skaffold https://storage.googleapis.com/skaffold/releases/v1.14.0/skaffold-linux-amd64
chmod +x skaffold
mv skaffold /usr/bin/

# reg: Docker registry v2 command line client
REG_SHA256="ade837fc5224acd8c34732bf54a94f579b47851cc6a7fd5899a98386b782e228"
curl --retry 5 -fsSL "https://github.com/genuinetools/reg/releases/download/v0.16.1/reg-linux-amd64" -o /usr/bin/reg
echo "\${REG_SHA256} /usr/bin/reg" | sha256sum -c - && chmod +x /usr/bin/reg
"""
                        }
                        env.PLATFORM_VERSION = sh(script: 'mvn help:evaluate -Dexpression=nuxeo.platform.version -q -DforceStdout', returnStdout: true).trim()
                        if (env.CHANGE_TARGET) {
                            echo "PR build: cleaning up the branch artifacts..."
                            sh "reg rm \"${DOCKER_REGISTRY}/${ORG}/${APP_NAME}:${env.CHANGE_BRANCH}\" || true"
                        }
                        echo "AI_CORE_VERSION: $AI_CORE_VERSION"
                        echo "INSIGHT_DEMOS_VERSION: $INSIGHT_DEMOS_VERSION"
                        echo "PLATFORM_VERSION: $env.PLATFORM_VERSION" // resolved version (ie: 11.5.88)
                        echo "VERSION: $env.VERSION"
                    }
                }
            }
            post {
                always {
                    setGitHubBuildStatus('init')
                }
            }
        }
        stage('Maven Build') {
            steps {
                container('platform11') {
                    script {
                        stepsMaven.build(PACKAGE_PATTERN)
                    }
                }
            }
            post {
                always {
                    setGitHubBuildStatus('maven/build')
                }
            }
        }
        stage('Docker Build') {
            steps {
                container('platform11') {
                    dir('packages') {
                        unstash 'packages'
                        // remove version from package filenames and copy to Docker
                        sh '''#!/bin/bash -xe
PACKAGES="\$(ls $PACKAGE_PATTERN)"
for file in \$PACKAGES ; do
    cp $file ../docker/$(basename ${file%-[0-9]*}).zip
done
'''
                    }
                    withCredentials([usernameColonPassword(credentialsId: 'connect-nuxeo-ai-jx-bot', variable: 'CONNECT_CREDS_PROD'),
                                     string(credentialsId: 'instance-clid', variable: 'CLID')]) {
                        sh '''
curl -fsSL -u "$CONNECT_CREDS_PROD" "$MARKETPLACE_URL/package/nuxeo-web-ui/download?version=$WEBUI_VERSION" -o docker/nuxeo-web-ui.zip --retry 10 --retry-max-time 600
curl -fsSL -u "$CONNECT_CREDS_PROD" "$MARKETPLACE_URL_PREPROD/package/nuxeo-csv/download?version=$PLATFORM_VERSION" -o docker/nuxeo-csv.zip --retry 10 --retry-max-time 600
'''
                    }
                    withCredentials([usernamePassword(credentialsId: 'packages-deployment-jx',
                            usernameVariable: 'PACKAGES_USER', passwordVariable: 'PACKAGES_PASSWORD')]) {
                        sh 'envsubst < docker/nuxeo-private.repo > docker/nuxeo-private.repo~gen'
                    }
                    dir('docker') {
                        sh 'printenv|sort|grep VERSION'
                        sh '''#!/bin/bash -e
envsubst < skaffold.yaml > skaffold.yaml~gen
skaffold build -f skaffold.yaml~gen
'''
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'docker/skaffold.yaml~gen'
                    setGitHubBuildStatus('docker/build')
                }
            }
        }
        stage('Maven Test') {
            when {
                not {
                    tag '*'
                }
            }
            environment {
                MAVEN_OPTS = "-Xms2g -Xmx2g"
                AWS_REGION = "eu-west-1"
                TEST_NAMESPACE = jx.normalizeNS("$APP_NAME-$BRANCH_NAME")
                DOMAIN_SUFFIX = "${TEST_NAMESPACE}.svc.cluster.local"
                TEST_CHART_NAME = "${APP_NAME}-test"
                MONGODB_WR = "${TEST_CHART_NAME}-mongodb"
                MONGODB_MAVEN_ARGS = "-Pmongodb" +
                        " -Dnuxeo.test.mongodb.dbname=nuxeo" +
                        " -Dnuxeo.test.mongodb.server=mongodb://${MONGODB_WR}.${DOMAIN_SUFFIX}"
                ELASTICSEARCH_WR = "elasticsearch-master"
                ELASTICSEARCH_MAVEN_ARGS = "-Dnuxeo.test.elasticsearch.addressList=http://${ELASTICSEARCH_WR}.${DOMAIN_SUFFIX}:9200"
                KAFKA_WR = "${TEST_CHART_NAME}-kafka"
                KAFKA_POD_NAME = "${TEST_CHART_NAME}-kafka-0"
                KAFKA_MAVEN_ARGS = "-Pkafka -Dkafka.bootstrap.servers=${KAFKA_WR}.${DOMAIN_SUFFIX}:9092"
                BRANCH_NAME_LABEL = jx.normalizeLabel(BRANCH_NAME)
            }
            steps {
                container('platform11') {
                    script {
                        try {
                            dir('charts/utests') {
                                echo 'Install external services from Nuxeo Helm chart'
                                sh """#!/bin/bash -xe
kubectl delete ns ${TEST_NAMESPACE} --ignore-not-found=true --now
kubectl create ns ${TEST_NAMESPACE}
make build
jx step helm install ./ -n ${TEST_CHART_NAME} --namespace=${TEST_NAMESPACE}
kubectl -n ${TEST_NAMESPACE} rollout status deployment ${MONGODB_WR} --timeout=5m
kubectl -n ${TEST_NAMESPACE} rollout status statefulset ${ELASTICSEARCH_WR} --timeout=5m
kubectl -n ${TEST_NAMESPACE} rollout status statefulset ${KAFKA_WR} --timeout=5m
"""
                            }
                            withCredentials([[$class       : 'AmazonWebServicesCredentialsBinding',
                                              credentialsId: 'aws-762822024843-ai-ci-role']]) {
                                stepsMaven.test(MONGODB_MAVEN_ARGS, ELASTICSEARCH_MAVEN_ARGS, KAFKA_MAVEN_ARGS)
                            }
                        } finally {
                            sh """#!/bin/bash
kubectl -n ${TEST_NAMESPACE} logs --selector=app=mongodb --tail=-1 > mongodb.log
kubectl -n ${TEST_NAMESPACE} logs --selector=app=elasticsearch --all-containers --tail=-1 > elasticsearch.log
kubectl -n ${TEST_NAMESPACE} logs ${KAFKA_POD_NAME} > kafka.log
"""
                            if (env.CHANGE_TARGET && pullRequest?.labels?.contains('keepTestEnv')) {
                                echo """[keepTestEnv] The test namespace was not cleaned. Do it yourself:
jx step helm delete ${TEST_CHART_NAME} --namespace=${TEST_NAMESPACE} --purge
kubectl delete ns ${TEST_NAMESPACE} --ignore-not-found=true
"""
                            } else {
                                sh "jx step helm delete ${TEST_CHART_NAME} --namespace=${TEST_NAMESPACE} --purge"
                                sh "kubectl delete ns ${TEST_NAMESPACE} --ignore-not-found=true"
                            }
                        }
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/*-reports/*.xml, '
                    archiveArtifacts artifacts: '**/target/*.log, **/log/*.log, *.log, ' +
                            '**/target/*-reports/*, **/target/results/*.html, **/target/*.png, **/target/*.html, ' +
                            'charts/preview/values.yaml, charts/preview/extraValues.yaml, ' +
                            'charts/preview/requirements.lock',
                            allowEmptyArchive: true
                    setGitHubBuildStatus('maven/test')
                }
            }
        }
        stage('Maven Deploy') {
            when {
                anyOf {
                    tag '*'
                    branch 'master*'
                    branch 'sprint-*'
                    branch 'maintenance*'
                }
            }
            steps {
                container('platform11') {
                    script {
                        stepsMaven.deploy(PACKAGE_PATTERN)
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: PACKAGE_PATTERN.replaceAll(' ', ', '), allowEmptyArchive: false
                    setGitHubBuildStatus('maven/deploy')
                }
            }
        }
        stage('Deploy Preview') {
            when {
                anyOf {
                    branch 'master*'
                    branch 'sprint-*'
                    branch 'maintenance-*'
                    changeRequest()
                }
            }
            options {
                timeout(time: 30, unit: 'MINUTES')
            }
            environment {
                JX_NO_COMMENT = "${env.CHANGE_TARGET ? 'false' : 'true'}"
                PREFIX = "nuxeo/$APP_NAME PR-"
            }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    container('platform11') {
                        script {
                            withEnv(["BRANCH_NAME=${jx.normalizeLabel(BRANCH_NAME, PREFIX)}"]) {
                                jx.waitForNuxeo("preview", PREVIEW_URL) {
                                    dir('charts/preview') {
                                        sh """#!/bin/bash -xe
kubectl delete ns ${PREVIEW_NAMESPACE} --ignore-not-found=true --now
kubectl create ns ${PREVIEW_NAMESPACE}
make build
# detach process that would never succeed, patch the deployment, then reattach and wait
jx preview --namespace ${PREVIEW_NAMESPACE} --verbose --source-url=$GIT_URL --preview-health-timeout 15m --alias nuxeo --no-comment=$JX_NO_COMMENT &
until (kubectl -n ${PREVIEW_NAMESPACE} get deploy preview 2>/dev/null); do sleep 5; done
kubectl -n ${PREVIEW_NAMESPACE} scale deployment --replicas=0 preview
kubectl -n ${PREVIEW_NAMESPACE} patch deployments preview --patch "\$(cat patch-preview.yaml)"
kubectl -n ${PREVIEW_NAMESPACE} scale deployment --replicas=1 preview
wait
"""
                                    }
                                }
                            }
                        }
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'charts/preview/values.yaml, charts/preview/extraValues.yaml, ' +
                            'charts/preview/requirements.lock, charts/preview/.previewUrl, debug-preview.log'
                    setGitHubBuildStatus('charts/preview')
                }
            }
        }
        stage('Push Packages') {
            when {
                anyOf {
                    tag '*'
                    branch 'master*'
                    branch 'sprint-*'
                    branch 'maintenance*'
                }
            }
            steps {
                container('platform11') {
                    script {
                        uploadPackages('connect-nuxeo-ai-jx-bot', 'connect-preprod')
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: PACKAGE_PATTERN.replaceAll(' ', ','), allowEmptyArchive: false
                    setGitHubBuildStatus('package/push')
                }
            }
        }
        stage('Trigger Downstream Jobs') {
            when {
                not {
                    tag '*'
                }
            }
            steps {
                container('platform11') {
                    script {
                        jx.buildJob("nuxeo/nuxeo-ai-integration")
                    }
                }
            }
        }
        stage('Upgrade version stream') {
            when {
                tag '*'
            }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    container('platform11') {
                        script {
                            jx.upgradeVersionStream('packages/nuxeo-ai.yml')
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (env.BRANCH_NAME ==~ 'master.*' || env.TAG_NAME || env.BRANCH_NAME ==~ 'sprint-.*' || env.BRANCH_NAME ==~ 'maintenance.*') {
                    step([$class: 'JiraIssueUpdater', issueSelector: [$class: 'DefaultIssueSelector'], scm: scm])
                }
            }
        }
    }
}
