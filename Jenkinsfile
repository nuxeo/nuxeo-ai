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

String getMavenArgs() {
    def args = '-V -B -Pmarketplace,ftest,aws clean install'
    if (env.TAG_NAME) {
        args += ' deploy -P-nexus,release -DskipTests'
    } else if (env.BRANCH_NAME ==~ 'master.*') {
        args += ' deploy -P-nexus'
    } else if (env.BRANCH_NAME ==~ 'sprint.*') {
        args += ' deploy -Pnexus'
    } else {
        args += ' package'
    }
    return args
}

/**
 * Normalize a string as a K8s namespace.
 * The pattern is '[a-z0-9]([-a-z0-9]*[a-z0-9])?' with a max length of 63 characters.
 */
String normalizeNS(String namespace) {
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
String normalizeLabel(String branchName) {
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
 */
void buildJob(String repo) {
    String targetBranch
    if (env.CHANGE_TARGET) { // Current is a PR
        targetBranch = env.CHANGE_BRANCH
        def prNumber = getPR(repo, "nuxeo:${targetBranch}")
        if (prNumber) {
            targetBranch = "PR-$prNumber"
        }
    } else {
        targetBranch = env.BRANCH_NAME
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

pipeline {
    agent {
        label "jenkins-ai-nuxeo1010"
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5'))
        timeout(time: 1, unit: 'HOURS')
    }
    environment {
        ORG = 'nuxeo'
        APP_NAME = 'nuxeo-ai'
        AI_CORE_VERSION = readMavenPom().getVersion()
        JIRA_AI_VERSION = readMavenPom().getProperties().getProperty('nuxeo-jira-ai.version')
        PLATFORM_VERSION = ''
        SCM_REF = "${sh(script: 'git show -s --pretty=format:\'%H%d\'', returnStdout: true).trim();}"
        PREVIEW_NAMESPACE = normalizeNS("$APP_NAME-$BRANCH_NAME")
        PREVIEW_URL = "https://preview-${PREVIEW_NAMESPACE}.ai.dev.nuxeo.com"
        VERSION = "${getVersion()}"
        PERSISTENCE = "${BRANCH_NAME == 'master-10.10'}"
        MARKETPLACE_URL = 'https://connect.nuxeo.com/nuxeo/site/marketplace'
        MARKETPLACE_URL_PREPROD = 'https://nos-preprod-connect.nuxeocloud.com/nuxeo/site/marketplace'
    }
    stages {
        stage('Init') {
            steps {
                setGitHubBuildStatus('init')
                container('platform1010') {
                    sh """#!/bin/bash
jx step git credentials
git config credential.helper store
"""
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
                    script {
                        PLATFORM_VERSION = sh(script: 'mvn help:evaluate -Dexpression=nuxeo.platform.version -q -DforceStdout', returnStdout: true).trim()
                        if (env.CHANGE_TARGET) {
                            echo "PR build: cleaning up the branch artifacts..."
                            sh """
reg rm "${DOCKER_REGISTRY}/${ORG}/${APP_NAME}:${VERSION}" || true
"""
                        }
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
            environment {
                MAVEN_OPTS = "-Xms1g -Xmx2g"
                MAVEN_ARGS = getMavenArgs()
                AWS_REGION = "us-east-1"
            }
            steps {
                setGitHubBuildStatus('build/maven')
                container('platform1010') {
//                    withAWS(region: AWS_REGION, credentials: 'aws-762822024843-jenkins-nuxeo-ai') { // jenkinsci/pipeline-aws-plugin#151
                    withCredentials([[$class       : 'AmazonWebServicesCredentialsBinding',
                                      credentialsId: 'aws-762822024843-jenkins-nuxeo-ai']]) {
                        sh 'mvn ${MAVEN_ARGS}'
                        sh "find . -name '*-reports' -type d"
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: '**/log/*.log, **/nxserver/config/distribution.properties, ' +
                            '**/target/*-reports/*, **/target/results/*.html, **/target/*.png, **/target/*.html',
                            allowEmptyArchive: true
                    setGitHubBuildStatus('build/maven')
                }
            }
        }
        stage('Docker Build') {
            steps {
                setGitHubBuildStatus('build/docker')
                container('platform1010') {
                    sh "cp nuxeo-ai-core-package/target/nuxeo-ai-core-*.zip docker/nuxeo-ai-core.zip"
                    withCredentials([string(credentialsId: 'instance-clid', variable: 'CLID')]) {
                        withEnv(["PLATFORM_VERSION=${PLATFORM_VERSION}"]) {
                            dir('docker') {
                                echo "Build preview image"
                                sh 'printenv|sort|grep VERSION'
                                sh """
envsubst < skaffold.yaml > skaffold.yaml~gen
skaffold build -f skaffold.yaml~gen
"""
                            }
                        }
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'docker/skaffold.yaml~gen'
                    setGitHubBuildStatus('build/docker')
                }
            }
        }
        stage('Deploy Preview') {
            when {
                anyOf {
                    branch 'master-10.10'
                    branch 'Sprint-*'
                    allOf {
                        changeRequest()
//                        expression {
//                            return pullRequest.labels.contains('preview')
//                        }
                    }
                }
            }
            steps {
                setGitHubBuildStatus('charts/preview')
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    container('platform1010') {
                        echo "Deploying AI Core as ${PREVIEW_URL}/nuxeo"
                        withCredentials([string(credentialsId: 'ai-insight-client-token', variable: 'AI_INSIGHT_CLIENT_TOKEN')]) {
                            withEnv(["PREVIEW_VERSION=$AI_CORE_VERSION", "BRANCH_NAME=${normalizeLabel(BRANCH_NAME)}"]) {
                                dir('charts/preview') {
                                    sh """#!/bin/bash -xe
kubectl delete ns ${PREVIEW_NAMESPACE} --ignore-not-found=true
kubectl create ns ${PREVIEW_NAMESPACE}
make preview
jx preview --namespace ${PREVIEW_NAMESPACE} --verbose --source-url=$GIT_URL --preview-health-timeout 15m --alias nuxeo
"""
                                    sh "jx get preview  -o json |jq '.items|map(select(.spec.namespace==\"${PREVIEW_NAMESPACE}\"))'"
                                    sh "cat .previewUrl"
                                }
                            }
                        }
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'charts/preview/values.yaml, charts/preview/extraValues.yaml, ' +
                            'charts/preview/requirements.lock'
                    setGitHubBuildStatus('charts/preview')
                }
            }
        }
        stage('Push Packages') {
            when {
                anyOf {
                    tag '*'
                    branch 'master-10.10'
                    branch 'Sprint-*'
                }
            }
            environment {
                PACKAGE_PATTERN = 'addons/*-package/target/nuxeo*package*.zip *-package/target/nuxeo-ai-core-*.zip'
            }
            steps {
                setGitHubBuildStatus('package/push')
                container('platform1010') {
                    withCredentials([usernameColonPassword(credentialsId: 'connect-nuxeo-ai-jx-bot', variable: 'CONNECT_CREDS'),
                                     usernameColonPassword(credentialsId: 'connect-preprod', variable: 'CONNECT_CREDS_PREPROD')]) {
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
            post {
                always {
                    archiveArtifacts artifacts: PACKAGE_PATTERN.replaceAll(' ', ', '), allowEmptyArchive: false
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
                container('platform1010') {
                    script {
                        buildJob("nuxeo/nuxeo-ai-integration")
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
                    container('platform1010') {
                        sh """#!/bin/bash -xe
jx step create pr regex --regex 'version: (.*)' --version $VERSION --files packages/nuxeo-ai.yml \
    -r https://github.com/nuxeo/jx-ai-versions --base core-2.x --branch core-2.x
"""
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (env.TAG_NAME || env.BRANCH_NAME == 'master-10.10' || env.BRANCH_NAME ==~ 'sprint-.*') {
                    step([$class: 'JiraIssueUpdater', issueSelector: [$class: 'DefaultIssueSelector'], scm: scm])
                }
            }
        }
    }
}
