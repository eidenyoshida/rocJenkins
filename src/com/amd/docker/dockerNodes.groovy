/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */
package com.amd.docker

import com.amd.project.*
import com.amd.docker.rocDocker

import java.nio.file.Path

import hudson.model.*
import hudson.slaves.*
import jenkins.model.*

class dockerNodes implements Serializable
{
    def dockerArray

    dockerNodes(def jenkinsGPULabels = ['gfx900'], String rocmVersion = 'rocm27', rocProject prj)
    {
        
        dockerArray = [:]
        String baseRunArgs = '--device=/dev/kfd --group-add=video --blkio-weight=20'
        
        jenkinsGPULabels.each
        {
            if(it.contains('cuda'))
            {
                dockerArray[it] = new rocDocker(
                                baseImage: 'nvidia/cuda:10.1-devel',
                                buildDockerfile: 'dockerfile-build-nvidia-cuda',
                                installDockerfile: 'dockerfile-install-nvidia-cuda',
                                runArgs: '--runtime=nvidia',
                                buildArgs: ' --pull',
                                infoCommands: """
                                                set -x 
                                                /usr/local/cuda/nvcc --version 
                                                pwd 
                                                nvidia-smi
                                            whoami
                                            """,
                                buildImageName:'build-' + prj.name.toLowerCase() + '-artifactory',
                                paths: prj.paths,
                                jenkinsLabel: it + " && " + rocmVersion
                        )
            }
            else if(it.contains('centos7') && it.contains('hip-clang'))
            {
                dockerArray[it] = new rocDocker(
                                baseImage: 'amdkila/centos-hip-clang:2.7',
                                buildDockerfile: 'dockerfile-build-centos',
                                installDockerfile: 'dockerfile-install-centos',
                                runArgs: baseRunArgs,
                                buildArgs: '--pull',
                                infoCommands: """
                                                set -x 
                                                /opt/rocm/bin/hipcc --version 
                                                pwd 
                                                dkms status
                                            whoami
                                            """,
                                buildImageName:'build-' + prj.name.toLowerCase() + '-artifactory',
                                paths: prj.paths,
                                jenkinsLabel: it + " && " + rocmVersion
                        )
            }
            else if(it.contains('ubuntu') && it.contains('hip-clang'))
            {
                dockerArray[it] = new rocDocker(
                                baseImage: 'amdkila/hip-clang:2.7',
                                buildDockerfile: 'dockerfile-build-ubuntu-rock',
                                installDockerfile: 'dockerfile-install-ubuntu',
                                runArgs: baseRunArgs,
                                buildArgs: '--pull',
                                infoCommands: """
                                                set -x 
                                                /opt/rocm/bin/hipcc --version 
                                                pwd 
                                                dkms status
                                                whoami
                                            """,
                                buildImageName:'build-' + prj.name.toLowerCase() + '-artifactory',
                                paths: prj.paths,
                                jenkinsLabel: it + " && " + rocmVersion
                        )
            }
            else if(it.contains('centos7'))
            {
                dockerArray[it] = new rocDocker(
                                baseImage: 'rocm/dev-centos-7:2.7',
                                buildDockerfile: 'dockerfile-build-centos',
                                installDockerfile: 'dockerfile-install-centos',
                                runArgs: baseRunArgs,
                                buildArgs: '--pull',
                                infoCommands: """
                                                set -x 
                                                /opt/rocm/bin/hcc --version 
                                                pwd 
                                                dkms status
                                            whoami
                                            """,
                                buildImageName:'build-' + prj.name.toLowerCase() + '-artifactory',
                                paths: prj.paths,
                                jenkinsLabel: it + " && " + rocmVersion
                        )
            }
            else if(it.contains('ubuntu'))
            {
                dockerArray[it] = new rocDocker(
                                baseImage: 'rocm/dev-ubuntu-16.04:2.7',
                                buildDockerfile: 'dockerfile-build-ubuntu-rock',
                                installDockerfile: 'dockerfile-install-ubuntu',
                                runArgs: baseRunArgs,
                                buildArgs: ' --pull',
                                infoCommands: """
                                                set -x 
                                                /opt/rocm/bin/hcc --version 
                                                pwd 
                                                dkms status
                                            whoami
                                            """, 
                                buildImageName:'build-' + prj.name.toLowerCase() + '-artifactory',
                                paths: prj.paths,
                                jenkinsLabel: it + " && " + rocmVersion
                        )
            }
        }
    }
}
