package com.eiliot.app.voice.remote

import android.os.*

/** Small synchronous transactions; only PCM callbacks are chunked (16 KB maximum). */
object SpeechWire {
    const val TOKEN="com.eiliot.app.speech.v062"
    const val REQUEST=IBinder.FIRST_CALL_TRANSACTION
    fun call(remote:IBinder,op:String,args:Bundle=Bundle(),callback:IBinder?=null):Bundle {
        val input=Parcel.obtain();val output=Parcel.obtain()
        try {
            input.writeInterfaceToken(TOKEN);input.writeString(op);input.writeBundle(args);input.writeStrongBinder(callback)
            check(remote.transact(REQUEST,input,output,0)) {"Speech worker rejected the request"}
            output.readException();val result=output.readBundle(SpeechWire::class.java.classLoader)?:Bundle()
            result.getString("error")?.let {error(it)};return result
        }finally {input.recycle();output.recycle()}
    }
}
