
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.dweb_browser.io.io.File
import org.dweb_browser.io.io.FileMode
import org.dweb_browser.io.io.ZipFile
import kotlin.test.Test

class Test {
  @Test
  fun `zip uncompress test`() = runBlocking{
    println("11111111")
    val file = File("/Volumes/T7/source/dweb_browser/next/kmp/io/src/commonTest/kotlin/", "2222.zip")

    println("22222")
    ZipFile(file).apply {
      val dir = File("/Volumes/T7/source/dweb_browser/next/kmp/io/src/commonTest/").resolve("kotlin2")
      extractToDirectory(
        dir,
        null,
      )
    }
    println("33333")

  }
}