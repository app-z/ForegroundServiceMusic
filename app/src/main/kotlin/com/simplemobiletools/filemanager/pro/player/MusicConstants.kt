package com.simplemobiletools.filemanager.pro.player


object MusicConstants {

    val NOTIFICATION_ID_FOREGROUND_SERVICE = 8466503
    val DELAY_SHUTDOWN_FOREGROUND_SERVICE: Long = 20000
    val DELAY_UPDATE_NOTIFICATION_FOREGROUND_SERVICE: Long = 10000

    val FILE_NAME_EXTRA_PARAM = "FILE_NAME_EXTRA_PARAM"

    object ACTION {

        val MAIN_ACTION = "music.action.main"
        val PAUSE_ACTION = "music.action.pause"
        val PLAY_ACTION = "music.action.play"
        val START_ACTION = "music.action.start"
        val STOP_ACTION = "music.action.stop"

    }

    object STATE_SERVICE {

        val PREPARE = 30
        val PLAY = 20
        val PAUSE = 10
        val NOT_INIT = 0


    }

    val MUSIC_FILE_EXT = arrayOf("mp3", "ogg", "wav", "aac", "flac", "mid", "xmf", "mp4", "m4a")
}
