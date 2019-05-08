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
            dockerArray[it] = new rocDocker(
                            buildImageName:'build-' + prj.name + '-artifactory',
                            paths: prj.paths,
                            jenkinsLabel: it + " && " + rocmVersion
                        )
        }
    }
}
