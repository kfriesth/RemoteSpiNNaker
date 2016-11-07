package uk.ac.manchester.cs.spinnaker.remote.web;

import org.springframework.core.convert.converter.Converter;

import uk.ac.manchester.cs.spinnaker.machine.SpinnakerMachine;

public class StringToSpinnakerMachine implements
		Converter<String, SpinnakerMachine> {
	@Override
	public SpinnakerMachine convert(String value) {
		return SpinnakerMachine.parse(value);
	}
}
