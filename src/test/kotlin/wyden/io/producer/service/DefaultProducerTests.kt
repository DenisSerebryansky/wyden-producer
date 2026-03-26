package wyden.io.producer.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHeaders
import org.springframework.scheduling.TaskScheduler
import wyden.io.producer.property.ProducerProperties
import java.time.Clock
import java.util.*
import kotlin.test.assertFalse

class DefaultProducerTests {

    private val rabbitTemplate = mock<RabbitTemplate>()

    private val clock = Clock.systemUTC()

    private val taskScheduler = mock<TaskScheduler>()

    private val properties = ProducerProperties(periodS = 1, useColors = false)

    private val producer = DefaultProducer(clock, rabbitTemplate, taskScheduler, properties)

    @Test
    fun `start should enable production`() {
        producer.start()
        assertTrue(producer.isStarted())
    }

    @Test
    fun `stop should disable production`() {
        producer.start()
        producer.stop()
        assertFalse(producer.isStarted())
    }

    @Test
    fun `should send task with correct routing key`() {
        producer.start()
        producer.produce()
        verify(rabbitTemplate).convertAndSend(
            eq("work-inbound"),
            startsWith("task.produced."),
            any<String>(),
            any<MessagePostProcessor>()
        )
    }

    @Test
    fun `receive outbound should send discarded when expired`() {
        val task = UUID.randomUUID().toString()
        val message = mock(Message::class.java) as Message<String>

        `when`(message.payload).thenReturn(task)
        `when`(message.headers).thenReturn(
            MessageHeaders(mapOf("expiresAt" to (System.currentTimeMillis() - 1000)))
        )

        producer.receiveOutboundTask(message)

        verify(rabbitTemplate).convertAndSend(
            eq("work-outbound"),
            eq("task.discarded.any"),
            eq("$task-discarded"),
        )
    }

    @Test
    fun `receive outbound should send certified when not expired`() {
        val task = UUID.randomUUID().toString()
        val message = mock(Message::class.java) as Message<String>

        `when`(message.payload).thenReturn(task)
        `when`(message.headers).thenReturn(
            MessageHeaders(mapOf("expiresAt" to (System.currentTimeMillis() + 10_000)))
        )

        producer.receiveOutboundTask(message)

        verify(rabbitTemplate).convertAndSend(
            eq("certified-result"),
            eq("task.certified.any"),
            eq("$task-certified"),
        )
    }
}
