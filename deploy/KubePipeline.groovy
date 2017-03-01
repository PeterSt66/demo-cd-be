def init() {
    echo "Initializing Kubernetes pipeline"
}

def prepareDeployment(namespace, projectId, featureId, instanceId, fullDeploy) {
      def deployTemplateFile = "deploy/kube-full-deploy.yaml"
      def deployThisYaml = "build/${projectId}-deploy.yaml"

      stage('prepare deploy') {
        gitlabCommitStatus("prepare_deploy") {
          echo "Making a the deployment descriptor for ${featureId}"

          unstash name: "deployDir"

          def sedCmd = "-e \"s/@VersionTagGoesHere@/${featureId}/\" "
          sedCmd = sedCmd + " -e \"s/@ProjectIdGoesHere@/${projectId}/\" "
          sedCmd = sedCmd + " -e \"s/@DockerImageRepoGoesHere@/${env.DOCKER_REPO}/\" "
          sedCmd = sedCmd + " -e \"s/@ClusterBaseNameGoesHere@/${env.CLUSTER_DNS_BASE}/\" "

           sh "sed ${sedCmd} ${deployTemplateFile}  >${deployThisYaml}"

          //sh "cat ${deployThisYaml}"

          stash name: "deployConfig", includes: "build/*.yaml"
        }
      } // stage
      return deployThisYaml
}


def cleanOldDeployment(namespace, instanceId, fullDeploy) {
      stage ("Clean Kubernetes ${namespace}:${instanceId}-${fullDeploy}") {
       if (fullDeploy) {
           echo "Cleaning Kubernetes for deployment ${namespace}:${instanceId}-${fullDeploy}"
           destroyInstance(namespace, instanceId)
        } // if
        echo "done cleaning Kubernetes for deployment ${instanceId}"
      } // stage
}

def deploy(namespace, projectId, featureId, deployThisYaml, fullDeploy) {
	echo "=================== Deploy ${namespace},${projectId},${featureId},${deployThisYaml},${fullDeploy} =================================="
    def instanceId = projectId + "-" + featureId;
    stage('deploy') {
        gitlabCommitStatus("k8s_deploy") {
          def verbose = 'true'

          if (!fullDeploy) {
            imageName = env.DOCKER_REPO + "/" + projectId + ":" + featureId;
            echo "Deploying master which is deployed, only kick its current deployment (image=${imageName})"
            deployLatest(namespace, instanceId, projectId, featureId )
            callKube(namespace, "label -f ${deployThisYaml} belongsTo=${instanceId} --overwrite=true")
          }
          else {
            echo "Starting full deployment - create and start"
            // full deploy when it's not the master
            // Get the OC deployment yaml config
            unstash name: "deployConfig"
            def createyaml = readFile file: deployThisYaml

        	try {
                // create OC resources
                callKube(namespace, "create -f ${deployThisYaml}")
                }
                finally {
                  // in a finally to label as much items in case of an error
                  callKube(namespace, "label -f ${deployThisYaml} belongsTo=${instanceId} --overwrite=true")
                }
              }
        }
      } // stage
}


def informAndDestroy(namespace, instanceId, isMaster) {
      stage('inform') {
        gitlabCommitStatus("inform") {
          addGitLabMRComment('CI: Deployed and tested OK')
        }
      }

      stage('destroy') {
        if (!isMaster) {
            timeout(time: 60, unit: 'MINUTES') {
                input 'Ready to destroy after use?'
            }

            node {
               destroyInstance(namespace, instanceId)
            }
        } // if
        echo "done cleaning Kubernetes for deployment ${instanceId}"
      } // stage
}






def deployLatest(namespace, instanceId, projectId, featureId) {
    def now = new Date().format("yyyyMMdd-HHmmss.SSS", TimeZone.getTimeZone('CET'))+"CET"
    template ='{"spec":{"template":{"spec":{"containers":[{"name":"'+projectId+'-pod-'+featureId+'","env":[{"name":"RESTARTED","value":"'+now+'"}]}]}}}}'

    callKube(namespace, "rollout history deploy ${instanceId}")

    callKube(namespace, "patch deployment ${instanceId} -p'${template}'")

    callKube(namespace, "rollout history deploy ${instanceId}")
}

def isDeployed(namespace, instanceId) {
    def dcFound = callKube(namespace,"get deployments -o name")
    echo "dcFound.indexOf('${instanceId}')=" + dcFound.indexOf(instanceId)
    return dcFound.indexOf(instanceId) >= 0
}


def destroyInstance(namespace, instanceId) {
    echo "Start scaling pods to zero on dc ${instanceId} in namespace ${namespace}"
    try {
       callKube(namespace, "scale deployment ${instanceId} --replicas=0 --timeout=1m")
    } catch (err) {
       echo "Error absorbed scaling pods to zero: ${err}"
    }
    // delete with our belongsTo label
    callKube(namespace, "delete deployment,svc,ingress --selector='belongsTo=$instanceId'")
    // rep-ctr needs another label selector
    callKube(namespace, "delete rc --selector='deploymentconfig=$instanceId'")
    echo "done cleaning Kubernetes for deployment ${instanceId}"
}

def callKube(namespace, cmd) {
    //script: "/usr/bin/kubectl $cmd --namespace=${namespace} --server=${env.K8S_MASTER} --token=${env.KUBE_AUTH_TOKEN} --insecure-skip-tls-verify=${env.SKIP_TLS}",
    def resStr = sh (
        script: "/usr/bin/kubectl $cmd --namespace=${namespace} --server=${env.K8S_MASTER}",
        returnStdout: true
    )
    echo "returned from $cmd: $resStr"
    return resStr
}

return this
