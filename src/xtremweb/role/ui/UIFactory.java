package xtremweb.role.ui;

/**
 *  <code>UIFactory</code> starts a new graphical user interface based
 *  on web pages
 *
 * @author <a href="mailto:fedak@lri.fr">Gilles Fedak</a>
 * @version 1.0
 */

import xtremweb.core.http.HttpServer;
import xtremweb.core.http.HttpServerFactory;
import xtremweb.core.conf.ConfigurationProperties;
import xtremweb.core.conf.ConfigurationException;
import xtremweb.core.log.Logger;
import xtremweb.core.log.LoggerFactory;
import java.util.Properties;

/**
 * This class applys the singleton patter over a HttpServer
 * @author jose
 *
 */
public class UIFactory {
    
    /**
     * The http server
     */
    private static HttpServer httpServer=null;
    
    /**
     * Logger
     */
    protected static Logger log = LoggerFactory.getLogger(HttpServer.class);

    /**
     *  <code>createUIFactory</code> will start a new web server if
     *  none is started yet. After it loads several servlet and
     *  associate respective URL 
     */
    public static void  createUIFactory() {
	Properties mainprop;
	try {
	    mainprop = ConfigurationProperties.getProperties();
	} catch (ConfigurationException ce) {
	    log.warn("No configuration about ui found " + ce); 
	    mainprop = new Properties();
	}

	boolean ui = (Boolean.valueOf(mainprop.getProperty("xtremweb.role.ui", "true"))).booleanValue();

	if (ui) {
	    log.debug("starting web-based ui");
	    if (httpServer==null) {
		try {
		    httpServer =  HttpServerFactory.getHttpServer();
		} catch (Exception e) {
		    log.warn("cannot start Http based ui : " + e);
		    e.printStackTrace();
		} 
	    }
	}
    }
}
