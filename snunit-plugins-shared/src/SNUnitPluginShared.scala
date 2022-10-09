package snunit.plugin

import scala.sys.process._
import java.io.IOException
import scala.util.control.NonFatal

private[plugin] class SNUnitPluginShared(logger: Logger) {
  def isUnitInstalled(): Boolean = {
    try {
      // collect stderr from unitd
      var err = ""
      val processLogger = ProcessLogger(_ => (), err += _)

      Seq("unitd", "--version").!!(processLogger)
      err.contains("unit version:")
    } catch {
      case NonFatal(e) =>
        logger.error("""Can't find unitd in the path.
          |Please install NGINX Unit first.
          |You can find instructions here: https://unit.nginx.org/installation""".stripMargin)
        false
    }
  }
  def unitControlSocket(): String = {
    Seq("unitd", "--help").!!.linesIterator
      .find(_.contains("unix:"))
      .get
      .replaceAll(".+unix:", "")
      .stripSuffix("\"")
  }
  
  private def doCurl(command: String*) = (Seq("curl", "-sL", "--unix-socket", unitControlSocket()) ++ command).!!
  def deployToNGINXUnit(executable: String, port: Int): Unit = {
    require(isUnitInstalled(), "unitd is not installed")
    val config = s"""{
      "applications": {
        "app": {
          "type": "external",
          "executable": "$executable"
        }
      },
      "listeners": {
        "*:$port": {
          "pass": "applications/app"
        }
      }
    }"""
    val result = doCurl(
      "-X",
      "PUT",
      "-d",
      config,
      "http://localhost/config"
    )
    logger.info(result)
  }

  def restartApp() = {
    val result = doCurl("http://localhost/control/applications/app/restart")

    logger.info(result)
  }
}