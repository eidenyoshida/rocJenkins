/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */
package com.amd.docker

import com.amd.project.*
import com.amd.docker.rocDocker


import java.nio.file.Path;

class dockerNodes implements Serializable
{
    def dockerArray

    dockerNodes(def jenkinsGPULabels = ['gfx900'], String rocmVersion = 'rocm24', rocProject prj)
    {
        dockerArray = [:]
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
                                buildImageName:'build-' + prj.name + '-artifactory',
                                paths: prj.paths,
                                jenkinsLabel: it + " && " + rocmVersion
                        )
            }
            else if(it.contains('centos7'))
            {
                dockerArray[it] = new rocDocker(
                                baseImage: 'rocm/dev-centos-7:2.4',
                                buildDockerfile: 'dockerfile-build-centos',
                                installDockerfile: 'dockerfile-install-centos',
                                runArgs: '--device=/dev/kfd --device=/dev/dri --group-add=video',
                                buildArgs: '--pull',
                                infoCommands: """
                                                set -x 
                                                /opt/rocm/bin/hcc --version 
                                                pwd 
                                                dkms status
                                            whoami
                                            """,
                                buildImageName:'build-' + prj.name + '-artifactory',
                                paths: prj.paths,
                                jenkinsLabel: it + " && " + rocmVersion
                        )
            }
            else
            {
                dockerArray[it] = new rocDocker(
                                baseImage: 'rocm/dev-ubuntu-16.04:2.4',
                                buildDockerfile: 'dockerfile-build-ubuntu-rock',
                                installDockerfile: 'dockerfile-install-ubuntu',
                                runArgs: '--device=/dev/kfd --device=/dev/dri --group-add=video',
                                buildArgs: ' --pull',
                                infoCommands: """
                                                set -x 
                                                /opt/rocm/bin/hcc --version 
                                                pwd 
                                                dkms status
                                            whoami
                                            """, 
                                buildImageName:'build-' + prj.name + '-artifactory',
                                paths: prj.paths,
                                jenkinsLabel: it + " && " + rocmVersion
                        )
            }
        }
    }
}
