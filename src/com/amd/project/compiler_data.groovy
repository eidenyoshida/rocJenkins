/* ************************************************************************
 * Copyright 2018-2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.project

// Docker related variables gathered together to reduce parameter bloat on function calls
class compiler_data implements Serializable
{
    String compiler_name = 'hcc-rocm20-ubuntu' //default compiler name
    String build_config = 'Release'
    String compiler_path = '/opt/rocm/bin/hcc'
}
