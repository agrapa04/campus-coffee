package de.seuhd.campuscoffee.data.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/**
 * The OSM API response: the node id and its tags. Deserialized from the OSM XML by
 * [OsmResponseDeserializer], which combines the root `osm` element and the nested `node` element.
 */
@JacksonXmlRootElement(localName = "osm")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = OsmResponseDeserializer::class)
data class OsmResponse(
    val id: Long? = null,
    val tags: Map<String, String> = emptyMap()
)
