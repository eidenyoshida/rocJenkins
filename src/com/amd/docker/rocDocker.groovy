/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.docker
import java.nio.file.Path;

// Docker related variables gathered together to reduce parameter bloat on function calls
class rocDocker implements Serializable
{
    String baseImage = 'rocm/dev-ubuntu-16.04:2.4'
    String buildDockerfile = 'dockerfile-build-ubuntu-rock'
    String installDockerfile = 'dockerfile-install-ubuntu'
    String runArgs = '--device=/dev/kfd --device=/dev/dri --group-add=video'
    String buildArgs = ' --pull'
    String buildImageName
    String jenkinsLabel
    
    def insideClosure = """
          set -x
          /opt/rocm/bin/hcc --version
          pwd
          dkms status
      whoami
      """      
    
    def image
    def paths
    
    void buildImage(def stage)
    {    
        stage.dir( paths.project_src_prefix )
        {
                def user_uid = stage.sh(script: 'id -u', returnStdout: true ).trim()
                
                // Docker 17.05 introduced the ability to use ARG values in FROM statements
                // Docker inspect failing on FROM statements with ARG https://issues.jenkins-ci.org/browse/JENKINS-44836
                // build_image = docker.build( "${paths.project_name}/${build_image_name}:latest", "--pull -f docker/${buildDockerfile} --build-arg user_uid=${user_uid} --build-arg base_image=${from_image} ." )
                
                // JENKINS-44836 workaround by using a bash script instead of docker.build()
                stage.sh "docker build -t ${paths.project_name}/${buildImageName}:latest -f docker/${buildDockerfile} ${buildArgs} --build-arg user_uid=${user_uid} --build-arg base_image=${baseImage} ."
                image = stage.docker.image( "${paths.project_name}/${buildImageName}:latest" )
                // Print system information for the log
                image.inside( runArgs )
                {
                    stage.sh(insideClosure)
                }
        }
    }

    void runCommand(def stage, def command)
    {
	image.inside(runArgs)
	{
            stage.sh(command)
	}
    }

    void archiveArtifacts(def stage, String artifactName)
    {
	image.inside(runArgs)
	{
	    stage.archiveArtifacts artifacts: artifactName, fingerprint: true
	}
    }
    
/*    
    void UploadDockerHub(String RemoteOrg)
    {
    // Do not treat failures to push to docker-hub as a build fail
    try
    {
        sh  """#!/usr/bin/env bash
          set -x
          echo inside sh
          docker tag ${local_org}/${image_name} ${remote_org}/${image_name}
        """

        docker_hub_image = image( "${remote_org}/${image_name}" )

        docker.withRegistry('https://registry.hub.docker.com', 'docker-hub-cred' )
        {
        docker_hub_image.push( "${env.BUILD_NUMBER}" )
        docker_hub_image.push( 'latest' )
        }
    }
    catch( err )
    {
        currentBuild.result = 'SUCCESS'
    }
    }
*/
}
