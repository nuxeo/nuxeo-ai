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
        args += ' -Prelease deploy'
    }
    return args
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
        AI_CORE_VERSION = '3.0.0-SNAPSHOT'
        branch_name_lower_case = "${env.BRANCH_NAME.toLowerCase()}"
    }
    stages {
        stage('Build') {
            environment {
                MAVEN_OPTS = "$MAVEN_OPTS -Xms512m -Xmx1g"
                MAVEN_ARGS = getMavenArgs()
                AWS_REGION = "us-east-1"
            }
            steps {
                setGitHubBuildStatus('build')
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
                    junit allowEmptyResults: true, testResults: '**/target/*-reports/*.xml'
                    archiveArtifacts artifacts: '**/log/*.log, **/nxserver/config/distribution.properties, ' +
                            '**/target/*-reports/*, **/target/results/*.html, **/target/*.png, **/target/*.html',
                            allowEmptyArchive: true
                    setGitHubBuildStatus('build')
                }
            }
        }
        stage('Deploy Preview') {
            when {
                anyOf {
                    branch 'master'
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
                        sh "cp nuxeo-ai-core-package/target/nuxeo-ai-core-*.zip docker/"
                        dir('docker') {
                            echo "Build preview image"
                            sh """
envsubst < skaffold.yaml > skaffold.yaml~gen
skaffold build -f skaffold.yaml~gen
"""
                        }
                        withCredentials([string(credentialsId: 'ai-insight-client-token', variable: 'AI_INSIGHT_CLIENT_TOKEN')]) {
                            withEnv(["PREVIEW_VERSION=$AI_CORE_VERSION"]) {
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
                    branch 'master'
                    branch 'master-*'
                    branch 'Sprint-*'
                }
            }
            environment {
                MARKETPLACE_URL = 'https://connect.nuxeo.com/nuxeo/site/marketplace'
                MARKETPLACE_URL_PREPROD = 'https://nos-preprod-connect.nuxeocloud.com/nuxeo/site/marketplace'
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
                    setGitHubBuildStatus('package/push')
                    archiveArtifacts artifacts: PACKAGE_PATTERN.replaceAll(' ', ', '), allowEmptyArchive: false
                }
            }
        }
        stage('Deploy Preview') {
                    when {
                        anyOf {
                            branch 'master'
                            branch 'Sprint-*'
                            changeRequest()
                        }
                    }
                    steps {
                        setGitHubBuildStatus('charts/preview')
                        container('platform11') {
                            withEnv(["PREVIEW_VERSION=$AI_CORE_VERSION"]) {
                                dir('charts/preview') {
                                    sh """#!/bin/bash -xe
        # creating the namespace to be able to copy secret (make preview is having an error if we dont copy first)
        kubectl delete ns ai-nuxeo-nuxeo-ai-${branch_name_lower_case} --ignore-not-found=true
        kubectl create ns ai-nuxeo-nuxeo-ai-${branch_name_lower_case}
        kubectl get secret instance-clid --namespace=ai --export -o yaml | kubectl apply --namespace=ai-nuxeo-nuxeo-ai-${branch_name_lower_case} -f -
        make preview
        jx preview --source-url $GIT_URL
        kubectl delete --all pods --namespace=ai-nuxeo-nuxeo-ai-${branch_name_lower_case}
        """
                                }
                            }
                        }
                    }
                    post {
                        always {
                            setGitHubBuildStatus('charts/preview')
                        }
                    }
    }
    post {
        always {
            script {
                if (env.TAG_NAME || env.BRANCH_NAME == 'master' || env.BRANCH_NAME ==~ 'master-.*' || env.BRANCH_NAME ==~ 'Sprint-.*') {
                    step([$class: 'JiraIssueUpdater', issueSelector: [$class: 'DefaultIssueSelector'], scm: scm])
                }
            }
        }
    }
}
