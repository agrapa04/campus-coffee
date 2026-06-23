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
            tags = deserializeTags(p, nodeElement.get("tag"))
        )
    }

    /**
     * Reads the OSM tags into a map of keys to values, returning an empty map when no tags are present.
     * Jackson renders a single `<tag>` element as an object and multiple as an array, so both shapes are
     * handled (a single tag was previously dropped). A tag element missing its `k` or `v` attribute is a
     * malformed response, surfaced as a [JsonMappingException] so the OSM data service maps it to a 502.
     */
    private fun deserializeTags(
        p: JsonParser,
        tagNode: JsonNode?
    ): Map<String, String> {
        if (tagNode == null) {
            return emptyMap()
        }
        val tagElements = if (tagNode.isArray) tagNode.toList() else listOf(tagNode)
        return tagElements.associate { element ->
            val key = element.get("k") ?: throw JsonMappingException.from(p, "An OSM tag is missing its 'k' attribute.")
            val value =
                element.get("v") ?: throw JsonMappingException.from(p, "An OSM tag is missing its 'v' attribute.")
            key.asText() to value.asText()
        }
    }
}
