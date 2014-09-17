package uk.ac.manchester.cs.spinnaker.remote.web;

import org.springframework.core.convert.converter.Converter;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

public class StringToSpinnakerMachine
        implements Converter<String, SpinnakerMachine>{

	@Override
	public SpinnakerMachine convert(String value) {
		if (!value.startsWith("(") || !value.endsWith(")")) {
			throw new IllegalArgumentException(
					"Cannot convert string \"" + value
					+ "\" - missing start and end brackets");
		}
		String[] parts = value.substring(1, value.length() - 1).split(":");
		if (parts.length != 4) {
			throw new IllegalArgumentException(
					"Wrong number of :-separated arguments - " + parts.length
					+ " found but 4 required");
		}

		return new SpinnakerMachine(parts[0].trim(), parts[1].trim(),
				Integer.parseInt(parts[2].trim()),
				Integer.parseInt(parts[3].trim()));
	}

}
