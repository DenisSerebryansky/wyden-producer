package wyden.io.producer.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("producer")
data class ProducerProperties(
    val periodS: Long,
    val useColors: Boolean
)
