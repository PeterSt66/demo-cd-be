def init() {
    echo "Initializing Openshift pipeline"
}

def prepareDeployment(namespace, projectId, featureId, instanceId, fullDeploy) {
      def deployTemplateFile = "deploy/oc-full-deploy.yaml"
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
      stage ("Clean Openshift ${namespace}:${instanceId}-${fullDeploy}") {
       if (fullDeploy) {
           echo "Cleaning openshift for deployment ${namespace}:${instanceId}-${fullDeploy}"
           destroyInstance(namespace, instanceId)
        } // if
        echo "done cleaning openshift for deployment ${instanceId}"
      } // stage
}

def deploy(namespace, projectId, featureId, deployThisYaml, fullDeploy) {
    echo "=================== Deploy ${namespace},${projectId},${featureId},${deployThisYaml},${fullDeploy} =================================="
    def instanceId = projectId + "-" + featureId;
    stage('deploy') {
         gitlabCommitStatus("oc_deploy") {
          def verbose = 'true'

          if (!fullDeploy) {
            echo "Deploying master which is deployed, only kick its current deployment"
            deployLatest(namespace, instanceId)
            callOpenshift(namespace, "label -f ${deployThisYaml} belongsTo=${instanceId} --overwrite=true")
          }
          else {
            echo "Starting full deployment - create and start"
            // full deploy when it's not the master
            // Get the OC deployment yaml config
            unstash name: "deployConfig"
            def createyaml = readFile file: deployThisYaml

        	try {
                // create OC resources
                openshiftCreateResource jsonyaml: createyaml, namespace: namespace, authToken: env.OC_AUTH_TOKEN, verbose: verbose
            }
            finally {
              // in a finally to label as much items in case of an error
              callOpenshift(namespace, "label -f ${deployThisYaml} belongsTo=${instanceId} --overwrite=true")
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
        echo "done cleaning openshift for deployment ${instanceId}"
      } // stage
}

def deployLatest(namespace, instanceId) {
    callOpenshift(namespace, "deploy --latest ${instanceId}")
}

def isDeployed(namespace, instanceId) {
    def dcFound = callOpenshift(namespace,"get dc -o name")
    echo "dcFound.indexOf('${instanceId}')=" + dcFound.indexOf(instanceId)
    return dcFound.indexOf(instanceId) >= 0
}


def destroyInstance(namespace, instanceId) {
    echo "Start scaling pods to zero on dc ${instanceId} in namespace ${namespace}"
    try {
       callOpenshift(namespace, "scale dc ${instanceId} --replicas=0 --timeout=1m")
    } catch (err) {
       echo "Error absorbed scaling pods to zero: ${err}"
    }
    // delete with our belongsTo label
    callOpenshift(namespace, "delete is,dc,svc,route --selector='belongsTo=$instanceId'")
    // rep-ctr needs another label selector
    callOpenshift(namespace, "delete rc --selector='deploymentconfig=$instanceId'")
    echo "done cleaning openshift for deployment ${instanceId}"
}

def callOpenshift(namespace, cmd) {
    def resStr = sh (
        script: "/usr/bin/oc $cmd --namespace=${namespace} --server=${env.OC_MASTER} --token=${env.OC_AUTH_TOKEN} --insecure-skip-tls-verify=${env.SKIP_TLS}",
        returnStdout: true
    )
    echo "returned from $cmd:\n$resStr"
    return resStr
}

return this
