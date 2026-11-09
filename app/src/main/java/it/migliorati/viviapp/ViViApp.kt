package it.migliorati.viviapp

import android.app.Application

class ViViApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Mixer.init(this)
        MusicFinder.init(this)
    }
}
