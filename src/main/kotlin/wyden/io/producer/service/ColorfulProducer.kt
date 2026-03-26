package wyden.io.producer.service

import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.Message
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import wyden.io.producer.config.AmqpConfig.Companion.EXCHANGE_CERTIFIED
import wyden.io.producer.config.AmqpConfig.Companion.EXCHANGE_OUTBOUND
import wyden.io.producer.config.AmqpConfig.Companion.QUEUE_OUTBOUND
import wyden.io.producer.config.AmqpConfig.Companion.ROUTING_KEY_PREFIX_CERTIFIED
import wyden.io.producer.config.AmqpConfig.Companion.ROUTING_KEY_PREFIX_DISCARDED
import wyden.io.producer.config.AmqpConfig.Companion.ROUTING_KEY_PREFIX_INBOUND
import wyden.io.producer.model.Color
import wyden.io.producer.property.ProducerProperties
import wyden.io.producer.util.LoggerDelegate
import java.time.Clock
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@Service
@ConditionalOnProperty(
    prefix = "producer",
    name = ["use-colors"],
    havingValue = "true"
)
class ColorfulProducer(
    clock: Clock,
    rabbitTemplate: RabbitTemplate,
    taskScheduler: TaskScheduler,
    producerProperties: ProducerProperties
) : AbstractProducer(clock, rabbitTemplate, taskScheduler, producerProperties) {

    private val colors = Color.entries

    private val counter = AtomicInteger(0)

    @RabbitListener(queues = [QUEUE_OUTBOUND])
    fun receiveOutboundTask(message: Message<String>) {
        val task = message.payload
        val color = extractColor(message)
        val isExpired = isExpired(message)

        if (isExpired) {
            val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_DISCARDED, color)
            log.warn("Received processed task {} is expired, discarding with routing key {}", task, routingKey)
            sendTask(EXCHANGE_OUTBOUND, routingKey, "$task-discarded")
        } else {
            val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_CERTIFIED, color)
            log.info("Received valid processed task {}, certifying with routing key {}", task, routingKey)
            sendTask(EXCHANGE_CERTIFIED, routingKey, "$task-certified")
        }
    }

    override fun produce() {
        if (!productionEnabled.get()) return

        val color = getColor()
        val task = createTask(color)
        val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_INBOUND, color)

        log.info("Sending created task: {}, sending to routing key {}", task, routingKey)
        sendCreatedTask(task, routingKey)
    }

    private fun getColor(): Color = colors[Math.floorMod(counter.getAndIncrement(), colors.size)]

    private fun extractColor(message: Message<String>): Color? {
        val routingKey = message.headers[AmqpHeaders.RECEIVED_ROUTING_KEY] as? String
        val lastSubstring = routingKey?.substringAfterLast(".")
        return runCatching { lastSubstring?.let { Color.valueOf(it.uppercase()) } }.getOrNull()
    }

    private fun createTask(color: Color): String = "$color-${UUID.randomUUID()}"

    private fun getRoutingKey(prefix: String, color: Color?) = "$prefix.${(color?.toString() ?: "any").lowercase()}"

    companion object {
        private val log by LoggerDelegate()
    }
}
