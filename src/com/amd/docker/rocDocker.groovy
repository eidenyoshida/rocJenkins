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

    String listCPUs(def lowerBound, def upperBound, def stage)
    {
        String cpuRange = ""
        stage.dir( paths.project_src_prefix )
        {
            stage.sh(script: 'lstopo --only PU lstopo.txt --of console', returnStdout: false )    
    
            for(int id= lowerBound.toInteger(); id<= upperBound.toInteger(); id++)
            {
                cpuRange += stage.sh(script: "cat lstopo.txt | grep \"L#$id \" | awk \'{print \$3}\'| grep -o -E \"[0-9]+\" ", returnStdout: false ).trim()

                if(id != upperBound.toInteger())
                {
                    cpuRange += ','
                }
            }
        }
        return cpuRange
    }

    void buildImage(def stage)
    {           
        stage.dir( paths.project_src_prefix )
        {
            def user_uid = stage.sh(script: 'id -u', returnStdout: true ).trim()
    
            def systemCPUs = stage.sh(script: 'nproc', returnStdout: true ).trim()
            def CPUsPerExecutor = systemCPUs.toInteger().intdiv((stage.env.NUMBER_OF_EXECUTORS).toInteger())
            def containerCPUs_low = (stage.env.EXECUTOR_NUMBER).toInteger() * CPUsPerExecutor
            def containerCPUs_high = containerCPUs_low + CPUsPerExecutor - 1
            
            String containerRange = listCPUs(containerCPUs_low,containerCPUs_high, stage)

            runArgs += " --cpuset-cpus=\"${containerRange}\""       

            String imageLabel = jenkinsLabel.replaceAll("\\W","")

            // Docker 17.05 introduced the ability to use ARG values in FROM statements
            // Docker inspect failing on FROM statements with ARG https://issues.jenkins-ci.org/browse/JENKINS-44836
            stage.docker.build( "${paths.project_name}/${buildImageName}/${imageLabel}/${executorNumber}:latest", 
                               "--pull -f docker/${buildDockerfile} --build-arg user_uid=${user_uid} --build-arg base_image=${baseImage} --cpuset-cpus=\"${containerRange}\" .")

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
    
    // Outputs time in a string
    String timeFunction(int duration)
    {
        String stageTime
        int hours = 0
        int minutes = 0
        int seconds = 0

        if(duration > 60)
        {
            minutes = duration.intdiv(60) 
            seconds = duration % 60
            if(minutes > 60)
            {
                hours = minutes.intdiv(60)
                minutes = minutes % 60
            }
        }
        else
        {
            seconds = duration
        }
        
        if(minutes < 10 && seconds < 10)
        {
            stageTime = String.valueOf(hours) + ':0' + String.valueOf(minutes) + ':0' + String.valueOf(seconds)
        }
        else if(minutes < 10 && seconds >= 10)
        {
            stageTime = String.valueOf(hours) + ':0' + String.valueOf(minutes) + ':' + String.valueOf(seconds)
        }
        else if(minutes >= 10 && seconds < 10)
        {
            stageTime = String.valueOf(hours) + ':' + String.valueOf(minutes) + ':0' + String.valueOf(seconds)
        }   
        else
        {
            stageTime = String.valueOf(hours) + ':' + String.valueOf(minutes) + ':' + String.valueOf(seconds)
        }

        return stageTime
    }

    // Returns gpu name based on architecture
    String gpuLabel(String label)
    {
        String gpu

        if(label.contains('gfx803'))
        {
            gpu = 'Fiji'
        }
        else if(label.contains('gfx900'))
        {
            gpu = 'Vega 10'
        }
        else if(label.contains('gfx906'))
        {
            gpu = 'Vega 20'
        }
        else if(label.contains('gfx908'))
        {
            gpu = 'MI-100'
        } 
        else
        {
            gpu = 'dkms'
        }

        return gpu     
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
