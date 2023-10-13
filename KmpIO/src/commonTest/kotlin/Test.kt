
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.dweb_browser.io.io.ByteBuffer
import org.dweb_browser.io.io.File
import org.dweb_browser.io.io.FileMode
import org.dweb_browser.io.io.RawFile
import org.dweb_browser.io.io.ZipFile
import kotlin.test.Test

class Test {
  @Test
  fun `zip uncompress test`() = runBlocking{
    println("11111111")
    val file = File("/Users/biyou/src/kotlin/KmpIO/KmpIO/src/commonTest/kotlin/1111.zip")


    println("22222")
//    ZipFile(file).apply {
//      val dir = File("src/commonTest/kotlin/").resolve("kotlin2")
//      extractToDirectory(
//        dir,
//        null,
//      )
//    }
    val zipFile = ZipFile(file)
    var dir = File("/Users/biyou/src/kotlin/KmpIO/KmpIO/src/commonTest/kotlin/kotlin2")

    zipFile.extractToDirectory(dir, { zipEntry ->
      !zipEntry.name.contains("__MACOSX") && !zipEntry.name.contains(".DS_Store")
    })
    println("33333")

  }
}