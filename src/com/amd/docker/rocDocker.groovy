/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.docker

import com.amd.project.*
import java.nio.file.Path;

// Docker related variables gathered together to reduce parameter bloat on function calls
class rocDocker implements Serializable
{
    String baseImage
    String buildDockerfile
    String installDockerfile
    String runArgs
    String buildArgs
    String buildImageName
    String jenkinsLabel
    String executorNumber

    def infoCommands   
    def image
    def paths
        
    void buildImage(def stage)
    {           
        stage.dir( paths.project_src_prefix )
        {
            def user_uid = stage.sh(script: 'id -u', returnStdout: true ).trim()
            
            String imageLabel = jenkinsLabel.replaceAll("\\W","")
            
            // Docker 17.05 introduced the ability to use ARG values in FROM statements
            // Docker inspect failing on FROM statements with ARG https://issues.jenkins-ci.org/browse/JENKINS-44836
            stage.docker.build( "${paths.project_name}/${buildImageName}/${imageLabel}/${executorNumber}:latest", "--pull -f docker/${buildDockerfile} --build-arg user_uid=${user_uid} --build-arg base_image=${baseImage} .")

            // JENKINS-44836 workaround by using a bash script instead of docker.build()
            //stage.sh "docker build -t ${paths.project_name}/${buildImageName}/${imageLabel}/${executorNumber}:latest -f docker/${buildDockerfile} ${buildArgs} --build-arg user_uid=${user_uid} --build-arg base_image=${baseImage} ."
            
            image = stage.docker.image( "${paths.project_name}/${buildImageName}/${imageLabel}/${executorNumber}:latest" )
            
            // Print system information for the log
            image.inside( runArgs )
            {
                stage.sh(infoCommands)
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
