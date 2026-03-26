package wyden.io.producer.service

import jakarta.annotation.PostConstruct
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.messaging.Message
import org.springframework.scheduling.TaskScheduler
import wyden.io.producer.config.AmqpConfig.Companion.EXCHANGE_INBOUND
import wyden.io.producer.property.ProducerProperties
import wyden.io.producer.util.LoggerDelegate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

abstract class AbstractProducer(
    private val clock: Clock,
    private val rabbitTemplate: RabbitTemplate,
    private val taskScheduler: TaskScheduler,
    private val producerProperties: ProducerProperties
) {

    protected val productionEnabled: AtomicBoolean = AtomicBoolean(false)

    @PostConstruct
    fun init() {
        taskScheduler.scheduleAtFixedRate(::produce, Duration.ofSeconds(producerProperties.periodS))
    }

    internal abstract fun produce()

    fun isStarted(): Boolean = productionEnabled.get()

    fun start(): Boolean {
        val isStarted = productionEnabled.compareAndSet(false, true)
        if (isStarted) log.info("Tasks production has been started")
        return isStarted
    }

    fun stop(): Boolean {
        val isStopped = productionEnabled.compareAndSet(true, false)
        if (isStopped) log.info("Tasks production has been stopped")
        return isStopped
    }

    protected fun isExpired(message: Message<String>): Boolean {
        val expiresAt = message.headers[HEADER_EXPIRES_AT]?.toString()?.toLong() ?: return false
        return clock.instant().isAfter(Instant.ofEpochMilli(expiresAt))
    }

    protected fun sendCreatedTask(task: String, routingKey: String) {
        rabbitTemplate.convertAndSend(
            /* exchange = */ EXCHANGE_INBOUND,
            /* routingKey = */ routingKey,
            /* message = */ task
        ) { message ->
            val messageProperties = message.messageProperties
            messageProperties.setExpiration(TTL_MS.toString())
            messageProperties.headers[HEADER_EXPIRES_AT] = clock.instant().toEpochMilli() + TTL_MS
            message
        }
    }

    protected fun sendTask(exchange: String, routingKey: String, message: Any) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message)
    }

    companion object {
        private val log by LoggerDelegate()
        const val TTL_MS = 10_000L
        const val HEADER_EXPIRES_AT = "expiresAt"
    }
}