# ForegroundServiceMusic
Simple Foreground Service Music Player


![Notification](https://github.com/app-z/ForegroundServiceMusic/player.png)

This sample Music Player Based on <a href='https://github.com/SimpleMobileTools/Simple-File-Manager'>Simple-File-Manager</a>

Supported formates: 

```
    val MUSIC_FILE_EXT = arrayOf("mp3", "ogg", "wav", "aac", "flac", "mid", "xmf", "mp4", "m4a")
```

It is integrated by onClick method

```

    private fun itemClicked(item: FileDirItem) {
        if (item.isDirectory) {
            (activity as? MainActivity)?.apply {
                skipItemUpdating = isSearchOpen
                openedDirectory()
            }
            openPath(item.path)
        } else {
            val path = item.path
            if (isGetContentIntent) {
                (activity as MainActivity).pickedPath(path)
            } else if (isGetRingtonePicker) {
                if (path.isAudioFast()) {
                    (activity as MainActivity).pickedRingtone(path)
                } else {
                    activity?.toast(R.string.select_audio_file)
                }
            } else {
                val match = MUSIC_FILE_EXT.filter { it in path.extension() }
                if (!match.isEmpty()) {
                    tryPlayFile(path)
                } else {
                    activity!!.tryOpenPathIntent(path, false)
                }
            }
        }
    }

```


License
-------
    Copyright 2016-present SimpleMobileTools
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       https://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
