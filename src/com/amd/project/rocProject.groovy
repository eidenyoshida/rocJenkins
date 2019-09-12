/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.project

import com.amd.project.project_paths
import com.amd.mail.*
//import com.amd.project.compiler_data

import java.nio.file.Path

class rocProject implements Serializable
{
    def paths
    def compiler
    def timeout
    def email
    String name
    String testDirectory = 'build/release'
 
    class timeoutData
    {
        int permissions = 1
        int format = 3
        int packaging = 15
        int docker = 60
        int compile = 120
        int test = 300
    }
   
    rocProject(String name)
    {
        this.name = name
        paths = new project_paths(
            project_name: name.toLowerCase())
        compiler = new compiler_data()
        timeout = new timeoutData()
        email = new emailFunctions()
    }
}
