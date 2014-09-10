package uk.ac.manchester.cs.spinnaker.nmpi.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A deserialiser which deserialises classes based on unique properties that
 * they have.  The classes to be deserialised need to be registered with a
 * unique property using the "register" function
 */
public class PropertyBasedDeserialiser<T> extends StdDeserializer<T> {

	private static final long serialVersionUID = 1L;

	private Map<String, Class<? extends T>> registry =
			new HashMap<String, Class<? extends T>>();

	/**
	 * Creates a new deserialiser
	 */
	public PropertyBasedDeserialiser(Class<T> type) {
		super(type);
	}

	/**
	 * Registers a type against a property in the deserialiser
	 *
	 * @param propertyName The name of the unique property that identifies the
	 *                     class.  This is the JSON name
	 * @param type The class to register against the property
	 */
	public void register(String propertyName, Class<? extends T> type) {
		registry.put(propertyName, type);
	}

	@Override
	public T deserialize(JsonParser parser,
			DeserializationContext context) throws IOException,
			JsonProcessingException {
		ObjectMapper mapper = (ObjectMapper) parser.getCodec();
		ObjectNode root = (ObjectNode) mapper.readTree(parser);
		Class<? extends T> responseClass = null;
		Iterator<Entry<String, JsonNode>> elementsIterator = root.fields();
		while (elementsIterator.hasNext()) {
			Entry<String, JsonNode> element = elementsIterator.next();
			String name = element.getKey();
			if (registry.containsKey(name)) {
				responseClass = registry.get(name);
				break;
			}
		}
		if (responseClass == null) {
			return null;
		}
		return mapper.treeToValue(root, responseClass);
	}

}
