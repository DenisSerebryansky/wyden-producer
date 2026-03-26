package wyden.io.producer.service

import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
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
import wyden.io.producer.property.ProducerProperties
import wyden.io.producer.util.LoggerDelegate
import java.time.Clock
import java.util.*

@Service
@ConditionalOnProperty(
    prefix = "producer",
    name = ["use-colors"],
    havingValue = "false",
    matchIfMissing = true
)
class DefaultProducer(
    clock: Clock,
    rabbitTemplate: RabbitTemplate,
    taskScheduler: TaskScheduler,
    producerProperties: ProducerProperties
) : AbstractProducer(clock, rabbitTemplate, taskScheduler, producerProperties) {

    @RabbitListener(queues = [QUEUE_OUTBOUND])
    fun receiveOutboundTask(message: Message<String>) {
        val task = message.payload
        val isExpired = isExpired(message)

        if (isExpired) {
            val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_DISCARDED)
            log.warn("Received processed task {} is expired, discarding with routing key {}", task, routingKey)
            sendTask(EXCHANGE_OUTBOUND, routingKey, "$task-discarded")
        } else {
            val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_CERTIFIED)
            log.info("Received valid processed task {}, certifying with routing key {}", task, routingKey)
            sendTask(EXCHANGE_CERTIFIED, routingKey, "$task-certified")
        }
    }

    override fun produce() {
        if (!productionEnabled.get()) return

        val task = UUID.randomUUID().toString()
        val routingKey = getRoutingKey(ROUTING_KEY_PREFIX_INBOUND)

        log.info("Sending created task: {}, sending to routing key {}", task, routingKey)
        sendCreatedTask(task, routingKey)
    }

    private fun getRoutingKey(prefix: String) = "$prefix.any"

    companion object {
        private val log by LoggerDelegate()
    }
}
