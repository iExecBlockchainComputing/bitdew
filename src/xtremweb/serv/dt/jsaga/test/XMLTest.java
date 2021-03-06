package xtremweb.serv.dt.jsaga.test;

import org.junit.Test;

import xtremweb.serv.dt.jsaga.xml.XMLException;
import xtremweb.serv.dt.jsaga.xml.XmlDomImpl;
import junit.framework.TestCase;

/**
 * Test XML, test that the xml management interface correctly parse JSAGA fields
 * @author jsaray
 *
 */
public class XMLTest extends TestCase{
    
	/**
	 * Dom client
	 */
    private XmlDomImpl dom;
    
    /**
     * Set up
     */
    public void setUp(){
	try {
	    dom = new XmlDomImpl("VOMS","/Applications/JSAGA/etc/jsaga-default-contexts.xml");
	} catch (XMLException e) {
	    e.printStackTrace();
	    fail();
	}
    }
    
    /**
     * Test XML
     */
    @Test
    public void testXML(){
	
	assertEquals(dom.getElement("Server"),"voms://cclcgvomsli01.in2p3.fr:15011/O=GRID-FR/C=FR/O=CNRS/OU=CC-IN2P3/CN=cclcgvomsli01.in2p3.fr");
	assertEquals(dom.getElement("UserCert"),"/Users/josefrancisco/usercert.pem");
	assertEquals(dom.getElement("UserKey"),"/Users/josefrancisco/userkey.pem");
	assertEquals(dom.getElement("LifeTime"),"PT24H");
	
    }
    
    /**
     * Tear down
     */
    public void tearDown(){
	dom=null;
    }

}
