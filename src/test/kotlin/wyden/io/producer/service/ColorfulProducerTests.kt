package wyden.io.producer.service

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import org.springframework.scheduling.TaskScheduler
import wyden.io.producer.property.ProducerProperties
import java.time.Clock

class ColorfulProducerTests {

    private val rabbitTemplate = mock(RabbitTemplate::class.java)

    private val clock = Clock.systemUTC()

    private val taskScheduler = mock(TaskScheduler::class.java)

    private val properties = ProducerProperties(periodS = 1, useColors = true)

    private val producer = ColorfulProducer(clock, rabbitTemplate, taskScheduler, properties)

    @Test
    fun `produce should use round robin colors`() {
        producer.start()

        producer.produce()
        producer.produce()
        producer.produce()

        verify(rabbitTemplate).convertAndSend(
            eq("work-inbound"),
            eq("task.produced.red"),
            startsWith("RED-"),
            any(MessagePostProcessor::class.java),
        )
        verify(rabbitTemplate).convertAndSend(
            eq("work-inbound"),
            eq("task.produced.blue"),
            startsWith("BLUE-"),
            any(MessagePostProcessor::class.java),
        )
        verify(rabbitTemplate).convertAndSend(
            eq("work-inbound"),
            eq("task.produced.green"),
            startsWith("GREEN-"),
            any(MessagePostProcessor::class.java),
        )
    }

    @Test
    fun `receive outbound should certify using color from routing key`() {
        val message = mock(Message::class.java) as Message<String>

        `when`(message.payload).thenReturn("RED-uuid-processed")
        `when`(message.headers).thenReturn(
            MessageHeaders(
                mapOf(
                    "expiresAt" to (System.currentTimeMillis() + 10_000),
                    AmqpHeaders.RECEIVED_ROUTING_KEY to "task.processed.red",
                )
            )
        )

        producer.receiveOutboundTask(message)

        verify(rabbitTemplate).convertAndSend(
            eq("certified-result"),
            eq("task.certified.red"),
            eq("RED-uuid-processed-certified"),
        )
    }

    @Test
    fun `receive outbound should discard using color from routing key when expired`() {
        val message = mock(Message::class.java) as Message<String>

        `when`(message.payload).thenReturn("BLUE-uuid-processed")
        `when`(message.headers).thenReturn(
            MessageHeaders(
                mapOf(
                    "expiresAt" to (System.currentTimeMillis() - 1000),
                    AmqpHeaders.RECEIVED_ROUTING_KEY to "task.processed.blue",
                )
            )
        )

        producer.receiveOutboundTask(message)

        verify(rabbitTemplate).convertAndSend(
            eq("work-outbound"),
            eq("task.discarded.blue"),
            eq("BLUE-uuid-processed-discarded"),
        )
    }
}
