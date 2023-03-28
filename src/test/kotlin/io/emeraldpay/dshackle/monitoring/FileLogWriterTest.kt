package io.emeraldpay.dshackle.monitoring

import io.emeraldpay.dshackle.Global
import io.emeraldpay.dshackle.monitoring.record.AccessRecord
import io.emeraldpay.grpc.Chain
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.UUID

class FileLogWriterTest : ShouldSpec({

    should("write a log event") {
        val dir = Files.createTempDirectory("dshackle-test-").toFile()
        val accessLog = File(dir, "log.jsonl")
        println("Write log to ${accessLog.absolutePath}")

        val serializer: (Any) -> ByteArray? = {
            Global.objectMapper.writeValueAsBytes(it)
        }

        val logWriter = FileLogWriter(
            accessLog, serializer, Duration.ofMillis(10), Duration.ofMillis(10), 100
        )

        logWriter.start()
        val event = AccessRecord.Status(
            Chain.ETHEREUM, UUID.fromString("9d8ecbf3-12fb-49cf-af9d-949a1050a000"),
            AccessRecord.RequestDetails(
                UUID.fromString("9d8ecbf3-12fb-49cf-af9d-949a1050a000"),
                Instant.ofEpochMilli(1626746880123),
                AccessRecord.Remote(
                    listOf("127.0.0.1", "172.217.8.78"), "172.217.8.78", "UnitTest"
                )
            )
        )
        logWriter.submitAll(listOf(event))
        logWriter.flush()
        val act = accessLog.readLines()

        act shouldHaveSize 1
        val json = Global.objectMapper.readValue(act[0], Map::class.java)

        json["version"] shouldBe "accesslog/v1beta2"
        json["id"] shouldBe "9d8ecbf3-12fb-49cf-af9d-949a1050a000"
        json["method"] shouldBe "Status"
        json["blockchain"] shouldBe "ETHEREUM"
        (json["request"] as Map<String, Any>).also { request ->
            request["start"] shouldBe "2021-07-20T02:08:00.123Z"
            request["id"] shouldBe "9d8ecbf3-12fb-49cf-af9d-949a1050a000"
            (request["remote"] as Map<String, Any>).also { remote ->
                remote["ip"] shouldBe "172.217.8.78"
                remote["userAgent"] shouldBe "UnitTest"
            }
        }
    }
})
