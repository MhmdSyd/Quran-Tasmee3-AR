package com.quranrecite.app.speech.asr

import android.os.Debug
import android.os.Process

class AndroidResourceProbe : ResourceProbe {
    override fun cpuTimeMs(): Long = Process.getElapsedCpuTime()
    override fun nativeHeapBytes(): Long = Debug.getNativeHeapAllocatedSize()
    override fun pssKb(): Long {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        return mi.totalPss.toLong()
    }
}
