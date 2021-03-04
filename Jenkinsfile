/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/).
 * This is unpublished proprietary source code of Nuxeo SA. All rights reserved.
 * Notice of copyright on this source code does not indicate publication.
 *
 * Contributors:
 *     Julien Carsique <jcarsique@nuxeo.com>
 */

void setGitHubBuildStatus(String context) {
    step([
            $class       : 'GitHubCommitStatusSetter',
            reposSource  : [$class: 'ManuallyEnteredRepositorySource', url: 'https://github.com/nuxeo/nuxeo-ai'],
            contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: context],
            errorHandlers: [[$class: 'ShallowAnyErrorHandler']]
    ])
}

boolean isLongLivingBranch() {
    return env.BRANCH_NAME ==~ 'master.*|maintenance.*|sprint-.*'
}

String getMavenArgs(boolean skipAllTests = false) {
    def args = '-V -B -PJX'
    if (env.TAG_NAME) {
        args += ' -P-nexus,-nexus-private -Prelease'
    } else if (isLongLivingBranch()) {
        args += ' -P-nexus,-nexus-private'
    } else {
        args += ' -Pnexus'
    }
    if (skipAllTests || env.CHANGE_TARGET && pullRequest?.labels?.contains('skipTests')) {
        args += ' -DskipTests -DskipITs'
    }
    return args
}

/**
 * Normalize a string as a K8s namespace.
 * The pattern is '[a-z0-9]([-a-z0-9]*[a-z0-9])?' with a max length of 63 characters.
 */
static String normalizeNS(String namespace) {
    namespace = namespace.trim().substring(0, Math.min(namespace.length(), 63)).toLowerCase().replaceAll("[^-a-z0-9]", "-")
    assert namespace ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?/
    assert namespace.length() <= 63
    return namespace
}

/**
 * Normalize a string as a K8s label (prefix excluded).
 * The pattern is '<prefix/>?[0-9A-Za-z\-._]+' with a max length of 63 characters after the prefix.
 * 'jx preview' sets a default label '<Git Organisation> + "/" + <Git Name> + " PR-" + <PullRequestName?:-env.BRANCH_NAME>'.
 * Here we want to normalize the branch name.
 *
 */
static String normalizeLabel(String branchName) {
    int maxLength = 63 - "nuxeo/nuxeo-ai PR-".length()
    branchName = branchName.trim().substring(0, Math.min(branchName.length(), maxLength)).replaceAll("[^-._a-z0-9A-Z]", "-")
    assert branchName ==~ /[a-z0-9A-Z][-._0-9A-Za-z]*[a-z0-9A-Z]/
    assert branchName.length() <= maxLength
    return branchName
}

String getVersion() {
    String version = readMavenPom().getVersion()
    version = env.TAG_NAME ? version : version + "-" + env.BRANCH_NAME.replace('/', '-')
    assert version ==~ /[0-9A-Za-z\-._]*/
    return version
}

/**
 * Build repo job better corresponding to the current job, if exists.
 * If we're in a PR, then find the corresponding PR (if exists), else use the same working branch.
 * @param repo
 * @param maintenance ie: "10.10". If set, a prefix is added to the working branch: "10.10/<branch>". Except if the
 * current is a maintenance branch (master, sprint-*...), then a suffix is used instead of a prefix: "<branch>-10.10".
 */
void buildJob(String repo, String maintenance = null) {
    String prefix = ""
    String suffix = ""
    if (maintenance) {
        if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME ==~ 'sprint-.*') {
            suffix = "-$maintenance"
        } else {
            prefix = "$maintenance/"
        }
    }
    String targetBranch
    if (env.CHANGE_TARGET) { // Current is a PR
        targetBranch = prefix + env.CHANGE_BRANCH + suffix
        def prNumber = getPR(repo, "nuxeo:${targetBranch}")
        if (prNumber) {
            targetBranch = "PR-$prNumber"
        }
    } else {
        targetBranch = prefix + env.BRANCH_NAME + suffix
    }
    jobName = "/$repo/" + targetBranch.replace("/", "%2F")
    if (Jenkins.instance.getItemByFullName(jobName)) {
        echo "Triggering job /$repo build on branch $targetBranch"
        build job: jobName, propagate: false, wait: false
    } else {
        println("No job ${jobName} to trigger")
    }
}

String getPR(String repo, String branch) {
    escapedBranch = branch.replace("/", "%2F")
    withEnv(["REPO=${repo}", "BRANCH=${escapedBranch}"]) {
        withCredentials([string(credentialsId: 'github_token', variable: 'GITHUB_TOKEN')]) {
            String prNumber = sh(script: '''
curl -H "Authorization: token $GITHUB_TOKEN" https://api.github.com/repos/$REPO/pulls?head=$BRANCH|jq ".[].number"|head -n1
''', returnStdout: true).trim()
            return prNumber
        }
    }
}

/**
 * Wait for Nuxeo Kubernetes application's deployment, then wait for the pod being effectively ready
 * and finally check the running status.
 * In case of error, debug information is logged.
 * @param name Nuxeo app name
 * @param url Nuxeo URL
 */
void waitForNuxeo(String name, String url, Closure body = null) {
    script {
        try {
            body?.call()
            echo "Check deployment and running status for $name at $url ..."
            sh "kubectl -n ${PREVIEW_NAMESPACE} rollout status deployment $name"
            sh "kubectl -n ${PREVIEW_NAMESPACE} wait --for=condition=ready pod -l app=$name --timeout=-0"
            sh "curl --retry 10 -fsSL $url/nuxeo/runningstatus"
        } catch (e) {
            sh "jx get preview  -o json |jq '.items|map(select(.spec.namespace==\"${PREVIEW_NAMESPACE}\"))' 2>&1 |tee debug-${name}.log"
            sh "kubectl -n ${PREVIEW_NAMESPACE} get all,configmaps,endpoints,ingresses 2>&1 |tee -a debug-${name}.log"
            sh "kubectl -n ${PREVIEW_NAMESPACE} describe pod --selector=app=$name 2>&1 |tee -a debug-${name}.log"
            sh "kubectl -n ${PREVIEW_NAMESPACE} logs --selector=app=$name --all-containers --tail=-1 2>&1 |tee -a debug-${name}.log"
            echo "See debug info in debug-${name}.log"
            throw e
        }
    }
}

pipeline {
    agent {
        label "jenkins-ai-nuxeo1010"
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5'))
        timeout(time: 2, unit: 'HOURS')
    }
    environment {
        ORG = 'nuxeo'
        APP_NAME = 'nuxeo-ai'
        VERSION = getVersion()
        AI_CORE_VERSION = readMavenPom().getVersion()
        JIRA_AI_VERSION = readMavenPom().getProperties().getProperty('nuxeo-jira-ai.version')
        PLATFORM_VERSION = readMavenPom().getProperties().getProperty('nuxeo.latest.version')
        SCM_REF = "${sh(script: 'git show -s --pretty=format:\'%H%d\'', returnStdout: true).trim()}"
        PREVIEW_NAMESPACE = normalizeNS("$APP_NAME-$BRANCH_NAME")
        PREVIEW_URL = "https://preview-${PREVIEW_NAMESPACE}.ai.dev.nuxeo.com"
        PERSISTENCE = "${BRANCH_NAME ==~ 'master.*'}"
        PACKAGE_PATTERN = 'addons/*-package/target/nuxeo*package*.zip *-package/target/nuxeo-ai-core-*.zip'
        BRANCH_NAME_LABEL = normalizeLabel(BRANCH_NAME)
    }
    stages {
        stage('Init') {
            steps {
                setGitHubBuildStatus('init')
                setGitHubBuildStatus('maven/build')
                setGitHubBuildStatus('docker/build')
                script {
                    if (!env.TAG_NAME) {
                        setGitHubBuildStatus('maven/test')
                    }
                    if (env.TAG_NAME || isLongLivingBranch()) {
                        setGitHubBuildStatus('maven/deploy')
                    }
                    if (env.CHANGE_TARGET || isLongLivingBranch()) {
                        setGitHubBuildStatus('charts/preview')
                    }
                    if (env.TAG_NAME || isLongLivingBranch()) {
                        setGitHubBuildStatus('package/push')
                    }
                }
                container('nuxeo1010') {
                    sh "kubectl label pods ${NODE_NAME} branch=${BRANCH_NAME_LABEL}"
                    sh """#!/bin/bash -e
jx step git credentials
git config credential.helper store
"""
                    script {
                        if (env.CHANGE_TARGET) {
                            echo "PR build: cleaning up the branch artifacts..."
                            sh """
reg rm "${DOCKER_REGISTRY}/${ORG}/${APP_NAME}:${VERSION}" || true
"""
                        }
                    }
                    echo "AI_CORE_VERSION: $AI_CORE_VERSION"
                    echo "INSIGHT_DEMOS_VERSION: $INSIGHT_DEMOS_VERSION"
                    echo "PLATFORM_VERSION: $PLATFORM_VERSION"
                    echo "VERSION: $VERSION"
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
                container('nuxeo1010') {
                    sh "mvn clean install ${getMavenArgs(true)}"
                    stash name: 'packages', includes: PACKAGE_PATTERN.replaceAll(' ', ','), allowEmpty: false
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
                container('nuxeo1010') {
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
                    withCredentials([string(credentialsId: 'instance-clid', variable: 'CLID')]) {
                        withEnv(["PLATFORM_VERSION=${PLATFORM_VERSION}",
                                 'NUXEO_PACKAGES=/tmp/nuxeo-ai-core.zip /tmp/nuxeo-ai-aws-package.zip /tmp/nuxeo-ai-image-quality-package.zip']) {
                            dir('docker') {
                                sh 'printenv|sort|grep VERSION'
                                sh '''#!/bin/bash -e
envsubst < skaffold.yaml > skaffold.yaml~gen
skaffold build -f skaffold.yaml~gen
'''
                            }
                        }
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
                MAVEN_OPTS = "-Xms1g -Xmx1536m"
                AWS_REGION = "eu-west-1"
                TEST_NAMESPACE = normalizeNS("$APP_NAME-$BRANCH_NAME")
                DOMAIN_SUFFIX = "${TEST_NAMESPACE}.svc.cluster.local"
                TEST_CHART_NAME = "${APP_NAME}-test"
                MONGODB_WR = "${TEST_CHART_NAME}-mongodb"
                MONGODB_MAVEN_ARGS = "-Pmongodb" +
                        " -Dnuxeo.test.mongodb.dbname=nuxeo" +
                        " -Dnuxeo.test.mongodb.server=mongodb://${MONGODB_WR}.${DOMAIN_SUFFIX}"
                ELASTICSEARCH_WR = "${TEST_CHART_NAME}-elasticsearch-client"
                ELASTICSEARCH_MAVEN_ARGS = "-Dnuxeo.test.elasticsearch.addressList=http://${ELASTICSEARCH_WR}.${DOMAIN_SUFFIX}:9200"
                KAFKA_WR = "${TEST_CHART_NAME}-kafka"
                KAFKA_POD_NAME = "${TEST_CHART_NAME}-kafka-0"
                KAFKA_MAVEN_ARGS = "-Pkafka -Dkafka.bootstrap.servers=${KAFKA_WR}.${DOMAIN_SUFFIX}:9092"
            }
            steps {
                container('nuxeo1010') {
                    script {
                        try {
                            echo 'Install external services from Nuxeo Helm chart'
                            sh """#!/bin/bash -xe
kubectl delete ns ${TEST_NAMESPACE} --ignore-not-found=true --now
helm init --client-only --stable-repo-url=https://charts.helm.sh/stable
helm repo add elastic https://helm.elastic.co/
helm repo add platform https://chartmuseum.platform.dev.nuxeo.com
helm repo add ai https://chartmuseum.ai.dev.nuxeo.com
envsubst < helm/values.yaml > helm/values.yaml~gen
jx step helm install platform/nuxeo -v 1.0.14 -n ${TEST_CHART_NAME} --namespace=${TEST_NAMESPACE} --set-file=helm/values.yaml~gen
kubectl -n ${TEST_NAMESPACE} rollout status deployment ${MONGODB_WR} --timeout=5m
kubectl -n ${TEST_NAMESPACE} rollout status deployment ${ELASTICSEARCH_WR} --timeout=5m
kubectl -n ${TEST_NAMESPACE} rollout status statefulset ${KAFKA_WR} --timeout=5m
"""
//                    withAWS(region: AWS_REGION, credentials: 'aws-762822024843-jenkins-nuxeo-ai') { // jenkinsci/pipeline-aws-plugin#151
                            withCredentials([[$class       : 'AmazonWebServicesCredentialsBinding',
                                              credentialsId: 'aws-762822024843-jenkins-nuxeo-ai']]) {
                                sh "mvn test --fail-never -nsu ${getMavenArgs()} ${MONGODB_MAVEN_ARGS} ${ELASTICSEARCH_MAVEN_ARGS} ${KAFKA_MAVEN_ARGS}"
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
                    junit allowEmptyResults: true, testResults: '**/target/*-reports/*.xml'
                    archiveArtifacts artifacts: '**/target/*.log, **/log/*.log, *.log' +
                            ', **/target/*-reports/*, **/target/results/*.html, **/target/*.png, **/target/*.html',
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
                container('nuxeo1010') {
                    sh "mvn deploy -nsu ${getMavenArgs(true)}"
                    stash name: 'packages', includes: PACKAGE_PATTERN.replaceAll(' ', ','), allowEmpty: false
                }
            }
            post {
                always {
                    setGitHubBuildStatus('maven/deploy')
                }
            }
        }
        stage('Deploy Preview') {
            when {
                anyOf {
                    branch 'master-*'
                    branch 'sprint-*'
                    branch 'maintenance-*'
                    allOf {
                        changeRequest()
                    }
                }
            }
            options {
                timeout(time: 30, unit: 'MINUTES')
            }
            environment {
                JX_NO_COMMENT = "${env.CHANGE_TARGET ? 'false' : 'true'}"
            }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    container('nuxeo1010') {
                        withEnv(["PREVIEW_VERSION=$AI_CORE_VERSION", "BRANCH_NAME=${BRANCH_NAME_LABEL}"]) {
                            waitForNuxeo("preview", PREVIEW_URL) {
                                dir('charts/preview') {
                                    sh """#!/bin/bash -xe
kubectl delete ns ${PREVIEW_NAMESPACE} --ignore-not-found=true
kubectl create ns ${PREVIEW_NAMESPACE}
make preview

# detach process that would never succeed to patch the deployment, then reattach
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
            environment {
                MARKETPLACE_URL = 'https://connect.nuxeo.com/nuxeo/site/marketplace'
                MARKETPLACE_URL_PREPROD = 'https://nos-preprod-connect.nuxeocloud.com/nuxeo/site/marketplace'
            }
            steps {
                container('nuxeo1010') {
                    withCredentials([usernameColonPassword(credentialsId: 'connect-nuxeo-ai-jx-bot', variable: 'CONNECT_CREDS'),
                                     usernameColonPassword(credentialsId: 'connect-preprod', variable: 'CONNECT_CREDS_PREPROD')]) {
                        dir('packages') {
                            unstash 'packages'
                            sh '''
PACKAGES="\$(ls $PACKAGE_PATTERN)"
for file in \$PACKAGES ; do
    curl --fail -u "$CONNECT_CREDS_PREPROD" -F package=@\$file "$MARKETPLACE_URL_PREPROD/upload?batch=true" || true
    curl --fail -u "$CONNECT_CREDS" -F package=@\$file "$MARKETPLACE_URL/upload?batch=true"
done
'''
                        }
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
                container('nuxeo1010') {
                    script {
                        buildJob("nuxeo/nuxeo-ai-integration", "10.10")
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
                    container('nuxeo1010') {
                        sh """#!/bin/bash -xe
jx step create pr regex --regex 'version: (.*)' --version $VERSION --files packages/nuxeo-ai.yml \
    -r https://github.com/nuxeo/jx-ai-versions --base master-10.10 --branch master-10.10
"""
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
