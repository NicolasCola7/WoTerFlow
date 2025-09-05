package wot.directory

import com.google.common.io.Resources

/**
 * Represents a [Directory] configuration
 */
class DirectoryConfig {
    companion object {
        val BASE_URI = "http://example.com/ktwot/"
        val GRAPH_PREFIX = BASE_URI + "graph/"

        val contextV10 = "https://www.w3.org/2019/wot/td/v1"
        val contextV11 = "https://www.w3.org/2022/wot/td/v1.1"

        val ttlContextLocal = Resources.getResource("validation/td-validation.ttl").readText()
        val xmlShapesLocal = Resources.getResource("validation/shacl.xml").readText()
    }
}