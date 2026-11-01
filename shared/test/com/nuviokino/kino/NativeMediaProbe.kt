package com.nuviokino.kino

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import kotlinx.coroutines.delay

internal interface MpvProbeApi : Library {
    fun mpv_create(): Pointer?
    fun mpv_set_option_string(handle: Pointer,name: String,value: String): Int
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_command(handle: Pointer,args: StringArray): Int
    fun mpv_get_property_string(handle: Pointer,name: String): Pointer?
    fun mpv_free(pointer: Pointer)
    fun mpv_terminate_destroy(handle: Pointer)
}

/** Uses the same pinned libmpv binary as Nuvio Desktop, with output disabled for automated QA. */
internal suspend fun nativeVideoProbe(library: String, url: String): Pair<Int,Double> {
    val api=Native.load(library,MpvProbeApi::class.java)
    val handle=checkNotNull(api.mpv_create())
    fun property(name: String): String {
        val pointer=api.mpv_get_property_string(handle,name) ?: return ""
        return try { pointer.getString(0) } finally { api.mpv_free(pointer) }
    }
    try {
        for((name,value) in mapOf("vo" to "null","ao" to "null","hwdec" to "no","terminal" to "no","msg-level" to "all=fatal"))check(api.mpv_set_option_string(handle,name,value)>=0)
        check(api.mpv_initialize(handle)>=0)
        check(api.mpv_command(handle,StringArray(arrayOf("loadfile",url,"replace")))>=0)
        var width=0; var seconds=0.0
        for(attempt in 0..79) {
            width=property("video-params/w").toIntOrNull() ?: 0
            seconds=property("time-pos").toDoubleOrNull() ?: 0.0
            if(width>0 && seconds>0.1)return width to seconds
            delay(100)
        }
        error("libmpv did not decode live video: width=$width, time=$seconds")
    } finally { api.mpv_terminate_destroy(handle) }
}
