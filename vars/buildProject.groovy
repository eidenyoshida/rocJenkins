/* ************************************************************************
 * Copyright 2018 Advanced Micro Devices, Inc.
 * ************************************************************************ */

import com.amd.project.*
import com.amd.docker.rocDocker

def call(String nodeLogic, project_paths paths, rocDocker docker, compiler_data hcc_compiler_args, Closure body)
{   
    node ( nodeLogic )
    {
        echo "Starting Jenkins Job"
        
        stage ("Checkout source code")
        {
            build.checkout(paths)
        }
        
        stage ("Build Docker Container")
        {
            // Build a docker image that represents the library build environment
            docker.buildImage(this)
        }

        /*stage ("Compile Library")
        {
            paths.construct_build_prefix()
            docker.image.inside(docker.runArgs)
            {
                withEnv(["CXX=${hcc_compiler_args.compiler_path}", 'CLICOLOR_FORCE=1'])
                {
                  // Build library & clients
                  sh  """#!/usr/bin/env bash
                      set -x
                      cd ${paths.project_build_prefix}
                      LD_LIBRARY_PATH=/opt/rocm/hcc/lib ${paths.build_command}
                    """
                }                
            }
        }*/
        
        body()
    }

 
    
}

