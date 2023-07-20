package com.delta.server

import java.net.InetAddress

fun getIpAddress(): String = InetAddress.getLocalHost().hostAddress