/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.project

//import com.amd.project.*
import com.amd.project.project_paths
//import com.amd.project.compiler_data

import java.nio.file.Path

class rocProject implements Serializable
{
    def paths
    def compiler
    String name
    String testDirectory = 'build/release'
  
    def timeout
    
    class timeoutData
    {
          int docker = 2
	      int compile = 2
	      int test = 5
    }
    
    rocProject(String name)
    {
        this.name = name
        paths = new project_paths(
            project_name: name.toLowerCase())
        compiler = new compiler_data()
        timeout = new timeoutData()
    }
}
