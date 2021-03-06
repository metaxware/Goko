package org.goko.controller.tinyg.controller.bean;

public class TinyGExecutionError {
	private String title;
	private String message;
	private String errorMessage;
	
	
	public TinyGExecutionError() {}
	
	public TinyGExecutionError(String title, String message, String errorMessage) {
		super();
		this.title = title;
		this.message = message;
		this.errorMessage = errorMessage;
	}
	/**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}
	/**
	 * @param title the title to set
	 */
	public void setTitle(String title) {
		this.title = title;
	}
	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}
	/**
	 * @param message the message to set
	 */
	public void setMessage(String message) {
		this.message = message;
	}
	/**
	 * @return the errorMessage
	 */
	public String getErrorMessage() {
		return errorMessage;
	}
	/**
	 * @param errorMessage the errorMessage to set
	 */
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
