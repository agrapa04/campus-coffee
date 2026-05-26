package de.seuhd.campuscoffee.data;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Boot configuration used only by the data module's integration tests. It component-scans the data
 * layer so the real repositories, mappers, constraint retriever, custom repository base class, and
 * data services are wired exactly as in production, without pulling in the api or application layers.
 */
@SpringBootApplication
@EnableFeignClients
public class DataTestApplication {
}
