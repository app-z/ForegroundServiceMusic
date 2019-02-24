package com.simplemobiletools.filemanager.pro.extensions

fun String.isZipFile() = endsWith(".zip", true)

fun String.extension() = substringAfterLast('.', "")


fun String.fileName() = substringAfterLast('/', "")
