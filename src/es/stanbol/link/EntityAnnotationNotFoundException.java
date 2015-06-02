/**
 * 
 */
package es.stanbol.link;

/**
 * @author suryamani
 *
 */
public class EntityAnnotationNotFoundException extends Exception {
		
	/**
	 * 
	 */
	private static final long serialVersionUID = -1932913107786053866L;

	public EntityAnnotationNotFoundException() {
		super();
	}
	
	public EntityAnnotationNotFoundException(String message) {
		super(message);
	}
	
	public EntityAnnotationNotFoundException(String message, Throwable t) {
		super(message, t);
	}
	
}
