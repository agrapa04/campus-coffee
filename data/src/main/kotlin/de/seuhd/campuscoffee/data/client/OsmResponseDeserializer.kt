package de.seuhd.campuscoffee.data.client

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode

/**
 * Custom deserializer that extracts the node id and tags from the OSM XML's nested structure.
 */
class OsmResponseDeserializer : JsonDeserializer<OsmResponse>() {
    override fun deserialize(
        p: JsonParser,
        context: DeserializationContext
    ): OsmResponse {
        val root: JsonNode = p.codec.readTree(p)
        val nodeElement = root.get("node")

        if (nodeElement == null || !nodeElement.has("id") || !nodeElement.has("tag")) {
            throw JsonMappingException.from(p, "Missing required elements or attributes in OSM XML response.")
        }

        return OsmResponse(
            id = nodeElement.get("id").asLong(),
            tags = deserializeTags(nodeElement.get("tag"))
        )
    }

    private fun deserializeTags(tagNode: JsonNode?): Map<String, String> {
        if (tagNode == null || !tagNode.isArray) {
            return emptyMap()
        }
        return tagNode.associate { it.get("k").asText() to it.get("v").asText() }
    }
}
