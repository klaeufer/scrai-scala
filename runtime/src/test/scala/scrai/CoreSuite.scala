package edu.luc.cs.scrai

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit

class CoreSuite extends munit.FunSuite:
  test("comment prompts, including multiline, but never mixed code") {
    assertEquals(Prompt.extract(" // sort words\n// by length\n"), Some("sort words\nby length"))
    assertEquals(Prompt.extract("// \n//"), None)
    assertEquals(Prompt.extract("val x = 1 // help"), None)
    assertEquals(Prompt.extract("// help\nx"), None)
    assertEquals(Prompt.extract(""), None)
  }

  test("response cleaning preserves Scala and rejects empty, commands, and escapes") {
    assertEquals(Prompt.clean("```scala\ndef double(x: Int) =\n  x * 2\n```"), "def double(x: Int) =\n  x * 2")
    assertEquals(Prompt.clean("  List(1, 2).sum\n"), "List(1, 2).sum")
    intercept[IllegalArgumentException](Prompt.clean("  "))
    intercept[IllegalArgumentException](Prompt.clean(":quit"))
    intercept[IllegalArgumentException](Prompt.clean("\u001b[31mhello"))
  }

  test("named profiles override global settings and environment selects profile") {
    val text = """[scrai]
      |profile = groq
      |context_chars = 500
      |[groq]
      |model = example
      |[local]
      |base_url = http://localhost:8080/v1
      |model = local-model
      |""".stripMargin
    assertEquals(Config.parse(text).model, "example")
    assertEquals(Config.parse(text, Some("local")).model, "local-model")
    assertEquals(Config.parse(text).contextChars, 500)
    assertEquals(Config.parse("").hotkey, "^G")
    intercept[IllegalArgumentException](Config.parse(text, Some("missing")))
  }

  test("config validates keys, limits, and endpoint security") {
    for text <- Seq("typo = 1", "timeout_seconds = 0", "context_chars = -1", "temperature = 3",
      "base_url = http://example.com/v1", "base_url = https://user:secret@example.com/v1",
      "base_url = https://example.com/v1?key=secret", "hotkey = ^[g") do
      intercept[IllegalArgumentException](Config.parse(text))
  }

  test("session context is bounded, disabled when zero, and clearable") {
    val session = new SessionContext(8)
    session.add("12345")
    session.add("67890")
    assertEquals(session.snapshot, "5\n67890\n")
    session.clear()
    assertEquals(session.snapshot, "")
    val off = new SessionContext(0)
    off.add("secret")
    assertEquals(off.snapshot, "")
  }

  private def withServer(status: Int, response: String)(check: (GroqGenerator, () => String) => Unit): Unit =
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    @volatile var request = ""
    server.createContext("/v1/chat/completions", exchange => {
      assertEquals(exchange.getRequestMethod, "POST")
      assertEquals(exchange.getRequestHeaders.getFirst("Authorization"), "Bearer test-key")
      request = new String(exchange.getRequestBody.readAllBytes(), UTF_8)
      val bytes = response.getBytes(UTF_8)
      exchange.sendResponseHeaders(status, bytes.length)
      exchange.getResponseBody.write(bytes)
      exchange.close()
    })
    server.start()
    try
      val config = Config(baseUrl = s"http://127.0.0.1:${server.getAddress.getPort}/v1")
      check(new GroqGenerator(config, Map("GROQ_API_KEY" -> "test-key")), () => request)
    finally server.stop(0)

  test("HTTP client sends model, prompt, and context and parses code") {
    withServer(200, """{"choices":[{"message":{"content":"```scala\nwords.sorted\n```"},"finish_reason":"stop"}]}""") { (client, body) =>
      assertEquals(client.generate("sort words", "val words = List(\"a\")").get(5, TimeUnit.SECONDS), "words.sorted")
      val request = ujson.read(body())
      assertEquals(request("model").str, "openai/gpt-oss-120b")
      assert(request("messages")(1)("content").str.contains("sort words"))
      assert(request("messages")(1)("content").str.contains("val words"))
    }
  }

  test("rate-limit errors do not expose provider body or credentials") {
    withServer(429, "private provider details") { (client, _) =>
      val error = intercept[java.util.concurrent.ExecutionException] {
        client.generate("help", "").get(5, TimeUnit.SECONDS)
      }
      assert(error.getCause.getMessage.contains("429"))
      assert(!error.getCause.getMessage.contains("private"))
    }
  }

  test("truncated output is rejected") {
    withServer(200, """{"choices":[{"message":{"content":"def incomplete"},"finish_reason":"length"}]}""") { (client, _) =>
      val error = intercept[java.util.concurrent.ExecutionException](client.generate("help", "").get(5, TimeUnit.SECONDS))
      assert(error.getCause.getMessage.contains("truncated"))
    }
  }

  test("missing credentials fail locally") {
    intercept[IllegalArgumentException](new GroqGenerator(Config(), Map.empty).generate("help", ""))
  }