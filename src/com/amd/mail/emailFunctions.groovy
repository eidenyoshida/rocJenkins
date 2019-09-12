/* ************************************************************************
 * Copyright 2019 Advanced Micro Devices, Inc.
 * ************************************************************************ */

package com.amd.mail

import java.nio.file.Path

class emailFunctions implements Serializable
{
    int start()
    {
        return (int)System.currentTimeMillis().intdiv(1000)
    }
    
    int stop(int startTime)
    {
        int endTime = (int)System.currentTimeMillis().intdiv(1000)
        return endTime-startTime
    }

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
            gpu = 'gfx908'
        } 
        else
        {
            gpu = 'dkms'
        }

        return gpu     
    }
}
