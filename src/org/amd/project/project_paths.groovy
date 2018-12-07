/* ************************************************************************
 * Copyright 2018 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package org.project

// Paths variables bundled together to reduce parameter bloat on function calls
class project_paths implements Serializable
{
    String project_name
    String src_prefix
    String project_src_prefix
    String build_prefix
    String project_build_prefix
    String build_command
}