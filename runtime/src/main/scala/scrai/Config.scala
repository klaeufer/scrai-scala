package edu.luc.cs.scrai

import java.net.URI
import java.nio.file.{Files, Path}
import scala.collection.mutable

final case class Config(
    model: String = "openai/gpt-oss-120b",
    baseUrl: String = "https://api.groq.com/openai/v1",
    apiKeyEnv: String = "GROQ_API_KEY",
    temperature: Double = 0.2,
    maxTokens: Int = 2048,
    timeoutSeconds: Int = 30,
    contextChars: Int = 12000,
    hotkey: String = "^G",
    restoreHotkey: String = "^[g",
    instructions: String = "Prefer clear, concise examples suitable for live teaching."
)

object Config:
  def apiKeyConfigured(config: Config, env: Map[String, String] = sys.env): Boolean =
    env.get(config.apiKeyEnv).exists(_.nonEmpty)

  def load(env: Map[String, String] = sys.env): Config =
    val path = env.get("SCRAI_CONFIG").map(Path.of(_)).getOrElse {
      Path.of(env.getOrElse("XDG_CONFIG_HOME", System.getProperty("user.home") + "/.config"), "scrai.ini")
    }
    val text = if Files.exists(path) then Files.readString(path) else ""
    parse(text, env.get("SCRAI_PROFILE"))

  def parse(text: String, profileOverride: Option[String] = None): Config =
    val sections = mutable.Map.empty[String, mutable.Map[String, String]]
    var section = "scrai"
    text.linesIterator.zipWithIndex.foreach { (raw, index) =>
      val line = raw.trim
      if line.nonEmpty && !line.startsWith("#") && !line.startsWith(";") then
        if line.startsWith("[") && line.endsWith("]") then section = line.drop(1).dropRight(1).trim
        else
          val split = line.indexOf('=')
          require(split > 0, s"Invalid config line ${index + 1}")
          sections.getOrElseUpdate(section, mutable.Map.empty)(line.take(split).trim) = line.drop(split + 1).trim
    }
    val global = sections.get("scrai").map(_.toMap).getOrElse(Map.empty)
    val profile = profileOverride.orElse(global.get("profile"))
    val selected = profile.map { name =>
      sections.getOrElse(name, throw IllegalArgumentException(s"Unknown profile: $name")).toMap
    }.getOrElse(Map.empty)
    val values = (global - "profile") ++ selected
    val known = Set("model", "base_url", "api_key_env", "temperature", "max_tokens", "timeout_seconds",
      "context_chars", "hotkey", "restore_hotkey", "instructions")
    require((values.keySet -- known).isEmpty, s"Unknown config keys: ${(values.keySet -- known).mkString(", ")}")
    val d = Config()
    val c = Config(
      values.getOrElse("model", d.model), values.getOrElse("base_url", d.baseUrl),
      values.getOrElse("api_key_env", d.apiKeyEnv), values.get("temperature").fold(d.temperature)(_.toDouble),
      values.get("max_tokens").fold(d.maxTokens)(_.toInt),
      values.get("timeout_seconds").fold(d.timeoutSeconds)(_.toInt),
      values.get("context_chars").fold(d.contextChars)(_.toInt),
      values.getOrElse("hotkey", d.hotkey), values.getOrElse("restore_hotkey", d.restoreHotkey),
      values.getOrElse("instructions", d.instructions)
    )
    val uri = URI.create(c.baseUrl)
    require(uri.getHost != null && Set("https", "http").contains(uri.getScheme), "base_url must be an HTTP(S) URL")
    require(uri.getUserInfo == null && uri.getQuery == null && uri.getFragment == null, "base_url must not contain credentials, query, or fragment")
    require(uri.getScheme == "https" || Set("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost),
      "Use HTTPS except for a local model server")
    require(c.model.nonEmpty && c.apiKeyEnv.nonEmpty, "model and api_key_env must not be empty")
    require(c.temperature >= 0 && c.temperature <= 2, "temperature must be between 0 and 2")
    require(c.maxTokens > 0 && c.timeoutSeconds > 0 && c.contextChars >= 0, "Invalid token, timeout, or context limit")
    require(c.hotkey.nonEmpty && c.restoreHotkey.nonEmpty && c.hotkey != c.restoreHotkey, "Hotkeys must be nonempty and distinct")
    c