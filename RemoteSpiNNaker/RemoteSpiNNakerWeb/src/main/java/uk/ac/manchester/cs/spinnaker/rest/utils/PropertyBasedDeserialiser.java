package uk.ac.manchester.cs.spinnaker.rest.utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A deserialiser which deserialises classes based on unique properties that
 * they have. The classes to be deserialised need to be registered with a unique
 * property using the "register" function.
 */
public class PropertyBasedDeserialiser<T> extends StdDeserializer<T> {
	private static final long serialVersionUID = 1L;

	private final Map<String, Class<? extends T>> registry = new HashMap<>();

	/**
	 * Creates a new deserialiser
	 */
	public PropertyBasedDeserialiser(Class<T> type) {
		super(type);
	}

	/**
	 * Registers a type against a property in the deserialiser.
	 *
	 * @param propertyName
	 *            The name of the unique property that identifies the class.
	 *            This is the JSON name.
	 * @param type
	 *            The class to register against the property.
	 */
	public void register(String propertyName, Class<? extends T> type) {
		if (propertyName == null)
			throw new IllegalArgumentException("propertyName must be non-null");
		if (type == null)
			throw new IllegalArgumentException("type must be non-null");

		registry.put(propertyName, type);
	}

	@Override
	public T deserialize(JsonParser parser, DeserializationContext context)
			throws IOException, JsonProcessingException {
		ObjectNode root = parser.readValueAsTree();
		Iterator<String> elementsIterator = root.fieldNames();
		while (elementsIterator.hasNext()) {
			String name = elementsIterator.next();
			if (registry.containsKey(name))
				return parser.getCodec().treeToValue(root, registry.get(name));
		}
		return null;
	}
}
