/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

import com.amd.project.*
import com.amd.docker.rocDocker


import java.nio.file.Path;

def call(rocProject paths, def dockerArray, def runCode = {}, Closure body)
{
    def platforms =[:]

/*    for (platform in dockerArray)
    {
        platforms[platform.jenkinsLabel] = platform
    }
*/
    def action =
    { key ->
        def platform = dockerArray[key]

        node (platform.jenkinsLabel)
        {
            stage ("${platform.jenkinsLabel}") 
            {
                body(platform, runCode)
            }
        }
    }

    actions = [:]
    for (platform in dockerArray)
    {
        actions[platform.key] = action.curry(platform.key)
    }

    parallel actions
}