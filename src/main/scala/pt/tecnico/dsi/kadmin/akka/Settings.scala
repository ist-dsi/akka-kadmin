package pt.tecnico.dsi.kadmin.akka

import com.typesafe.config.{Config, ConfigFactory}

/**
  * This class holds all the settings that parameterize akka-kadmin.
  *
  * By default these settings are read from the Config obtained with `ConfigFactory.load()`.
  *
  * You can change the settings in multiple ways:
  *
  *  - Change them in the default configuration file (e.g. application.conf)
  *  - Pass a different config holding your configurations: {{{
  *       new Settings(yourConfig)
  *     }}}
  *     However it will be more succinct to pass your config directly to KadminActor: {{{
  *      context.actorOf(Props(classOf[KadminActor], yourConfig))
  *     }}}
  *  - Extend this class overriding the settings you want to redefine {{{
  *      object YourSettings extends Settings() {
  *        override val performDeduplication: Boolean = true
  *      }
  *      context.actorOf(Props(classOf[KadminActor], YourSettings))
  *    }}}
  *
  * @param config
  */
class Settings(config: Config = ConfigFactory.load()) {
  val akkaKadminConfig: Config = {
    val reference = ConfigFactory.defaultReference()
    val finalConfig = config.withFallback(reference)
    finalConfig.checkValid(reference, "akka-kadmin")
    finalConfig.getConfig("akka-kadmin")
  }
  import akkaKadminConfig._

  val performDeduplication = getBoolean("perform-best-effort-deduplication")

  override def toString: String = akkaKadminConfig.root.render
}

