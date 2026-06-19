package de.seuhd.campuscoffee.data

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

/**
 * Boot configuration used only by the data module's integration tests. It component-scans the data
 * layer so the real repositories, mappers, constraint mappings, id generator, and data services are
 * wired exactly as in production, without pulling in the api or application layers.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class DataTestApplication
