package org.dweb_browser.io.io

import org.dweb_browser.io.AppleCharset


actual class Charset actual constructor(set: Charsets) : AppleCharset(set) {
  actual val charset: Charsets = set

  actual override fun decode(bytes: ByteArray): String {
    return super.decode(bytes)
  }

  actual override fun encode(inString: String): ByteArray {
    return super.encode(inString)
  }
}