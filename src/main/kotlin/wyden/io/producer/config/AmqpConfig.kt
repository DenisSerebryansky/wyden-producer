package wyden.io.producer.config

import org.springframework.amqp.core.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AmqpConfig {

    @Bean
    fun workOutboundExchange(): TopicExchange = TopicExchange(EXCHANGE_OUTBOUND)

    @Bean
    fun workOutboundQueue(): Queue = QueueBuilder.durable(QUEUE_OUTBOUND).build()

    @Bean
    fun bindings(): Declarables =
        Declarables(
            BindingBuilder
                .bind(workOutboundQueue())
                .to(workOutboundExchange())
                .with(ROUTING_KEY_OUTBOUND)
        )

    companion object {
        const val EXCHANGE_INBOUND = "work-inbound"
        const val EXCHANGE_OUTBOUND = "work-outbound"
        const val EXCHANGE_CERTIFIED = "certified-result"

        const val ROUTING_KEY_OUTBOUND = "task.processed.*"

        const val ROUTING_KEY_PREFIX_INBOUND = "task.produced"
        const val ROUTING_KEY_PREFIX_CERTIFIED = "task.certified"
        const val ROUTING_KEY_PREFIX_DISCARDED = "task.discarded"

        const val QUEUE_OUTBOUND = "work-outbound-producer"
    }
}
