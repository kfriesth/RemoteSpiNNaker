package uk.ac.manchester.cs.spinnaker.job;

/**
 * Represents a stack trace provided remotely
 */
public class RemoteStackTraceElement {

	private String className = null;

	private String methodName = null;

	private String fileName = null;

	private int lineNumber = 0;

	public RemoteStackTraceElement() {

		// Does Nothing
	}

	public RemoteStackTraceElement(StackTraceElement element) {
		this.className = element.getClassName();
		this.methodName = element.getMethodName();
		this.fileName = element.getFileName();
		this.lineNumber = element.getLineNumber();
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

}
