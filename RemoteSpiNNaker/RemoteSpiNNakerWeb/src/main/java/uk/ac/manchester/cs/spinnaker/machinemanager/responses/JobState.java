package uk.ac.manchester.cs.spinnaker.machinemanager.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JobState {
	public static enum State {
		/** Job state is unknown. */
		UNKNOWN(0),
		/** Job is queued for execution. */
		QUEUED(1),
		/** Job is changing the power status of its assigned boards. */
		POWER(2),
		/** Job is executing or ready to execute. */
		READY(3),
		/** Job is deceased. */
		DESTROYED(4);
		public final int value;

		private State(int value) {
			this.value = value;
		}

		public static State get(int value) {
			for (State s : values())
				if (s.value == value)
					return s;
			return UNKNOWN;
		}
	}

	private int state;
	private Boolean power;
	private double keepAlive;
	private String reason;
	private Float startTime;

	public State getState() {
		return State.get(state);
	}

	public void setState(State state) {
		this.state = state.value;
	}

	public Boolean getPower() {
		return power;
	}

	public void setPower(Boolean power) {
		this.power = power;
	}

	@JsonProperty("keepalive")
	public double getKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(double keepAlive) {
		this.keepAlive = keepAlive;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	@JsonProperty("start_time")
	public Float getStartTime() {
		return startTime;
	}

	public void setStartTime(Float startTime) {
		this.startTime = startTime;
	}
}
