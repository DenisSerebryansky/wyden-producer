package wyden.io.producer.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import wyden.io.producer.service.AbstractProducer

@RestController
@Tag(name = "commands")
class ProducerController(private val producer: AbstractProducer) {

    @PostMapping("/start")
    fun start(): Boolean = producer.start()

    @PostMapping("/stop")
    fun stop(): Boolean = producer.stop()
}
