package xtremweb.serv.dt.dropbox;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.RequestTokenPair;
import com.dropbox.client2.session.Session.AccessType;
import com.dropbox.client2.session.WebAuthSession;
import com.dropbox.client2.session.WebAuthSession.WebAuthInfo;

import xtremweb.core.conf.ConfigurationException;
import xtremweb.core.conf.ConfigurationProperties;
import xtremweb.core.obj.dc.Data;
import xtremweb.core.obj.dc.Locator;
import xtremweb.core.obj.dr.Protocol;
import xtremweb.core.obj.dt.Transfer;
import xtremweb.serv.dt.BlockingOOBTransferImpl;
import xtremweb.serv.dt.OOBException;
import xtremweb.serv.dt.dropbox.sslsocket.EasySSLSocketFactory;

/**
 * This class implements a transfer using Dropbox protocol
 * @author josefrancisco
 *
 */
public class DropBoxTransfer extends BlockingOOBTransferImpl {
    
    /**
     * Factor to transform seconds to miliseconds
     */
    private long CONVERSION_RATIO=1000;
    /**
     * Properties file
     */
    private static Properties props;
    
    /**
     * Dropbox api
     */
    private static DropboxAPI api;
    
    /**
     * Dropbox Application key
     */
    private static String app_key;
    
    /**
     * Dropbox Application secret
     */
    private static String app_secret;
    
    /**
     * Web Authentication session
     */
    private static WebAuthSession was;
    
    /**
     * Dropbox generated access token key
     */
    private String access_token_key;
    
    /**
     * Drop box generated access token secret
     */
    private String access_token_secret;
    
    /**
     * Properties file where access token and secret will be stored
     */
    private String propertiesFile = "initialCredentials.properties";
    
    static {
	try {
	    props = ConfigurationProperties.getProperties();
	} catch (ConfigurationException e) {
	    log.fatal("Error while configuring ther properties ");
	    e.printStackTrace();
	}
    }
    
    /**
     * Default constructor
     */
    public DropBoxTransfer(){
	app_key = props.getProperty("xtremweb.serv.dr.dropbox.app-key");
	app_secret = props.getProperty("xtremweb.serv.dr.dropbox.app-secret");
    }
    
    /**
     * Dropbox transfer constructor
     * @param d data
     * @param t transfer
     * @param rl remote locator
     * @param ll local locator
     * @param rp remote protocol
     * @param lp local protocol
     * @throws OOBException if a problem happens
     */
    public DropBoxTransfer(Data d, Transfer t, Locator rl, Locator ll,
	    Protocol rp, Protocol lp) throws OOBException {
	super(d, t, rl, ll, rp, lp);
	app_key = props.getProperty("xtremweb.serv.dr.dropbox.app-key");
	app_secret = props.getProperty("xtremweb.serv.dr.dropbox.app-secret");
    }
    
    /**
     * Connect method, connects to dropbox using the Java SDK, if
     * there is no previously saved token secret and key , a couple
     * if generated, else a couple is searched on dropbox.properties file
     */
    public void connect() throws OOBException {

	app_key = props.getProperty("xtremweb.serv.dr.dropbox.app-key");
	app_secret = props.getProperty("xtremweb.serv.dr.dropbox.app-secret");
	log.debug("app key " + app_key + " app sec " + app_secret);
	AppKeyPair pair = new AppKeyPair(app_key, app_secret);
	was = new WebAuthSession(pair, AccessType.APP_FOLDER);
	
	HttpClient http_client = was.getHttpClient();
	http_client.getConnectionManager().getSchemeRegistry().register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	http_client.getConnectionManager().getSchemeRegistry().register(new Scheme("https", new EasySSLSocketFactory(), 443));
	WebAuthInfo info;
	access_token_key = readProperty("xtremweb.serv.dr.dropbox.token-key");
	access_token_secret = readProperty("xtremweb.serv.dr.dropbox.token-secret");

	if (access_token_key == null || access_token_key.equals("")
		|| access_token_secret == null
		|| access_token_secret.equals("")) {

	    try {
		info = was.getAuthInfo();
		RequestTokenPair tpair = info.requestTokenPair;
		log.info("the key generated is " + tpair.key
			+ " the secret generated is " + tpair.secret);
		String redirecturl = info.url;
		String exp = props
			.getProperty("xtremweb.serv.dr.dropbox.expiration");
		log.info("You have "
			+ exp
			+ " seconds to go to his url : "
			+ redirecturl
			+ " and grant bitdew permission in your dropbox account, this procedure will be done just once");
		Thread.sleep(Long.parseLong(exp)*CONVERSION_RATIO);
		AccessTokenPair at = was.getAccessTokenPair();
		log.info("Key " + at.key +" Secret " + at.secret);
		writePropertiesFile(at.key,at.secret);
		api = new DropboxAPI(was);
	    } catch (DropboxException e) {
		e.printStackTrace();
		throw new OOBException("A exception related to dropbox has appeared, please be sure to grant permission to bitdew, exception detail : " + e.getMessage());
	    } catch (NumberFormatException e) {
		e.printStackTrace();
		throw new OOBException(e.getMessage());
	    } catch (InterruptedException e) {
		e.printStackTrace();
		throw new OOBException(e.getMessage());
	    } catch (IOException e) {
		e.printStackTrace();
		throw new OOBException(e.getMessage());
	    }
	} else {
	    AccessTokenPair atp = new AccessTokenPair(access_token_key,
		    access_token_secret);
	    was.setAccessTokenPair(atp);
	    api = new DropboxAPI(was);
	}
    }
    
    /**
     * This method reads a specific property from the properties file containing the access token and secret
     * @param string
     * @return
     */
    private String readProperty(String string) {
	Properties Myproperties =  new Properties();
	String s="";
	try {
	    //load the data from a file of it exists
	    if ((new File(System.getProperty("user.dir") + File.separator + propertiesFile)).exists()) {
		InputStream data = new FileInputStream(propertiesFile);
		Myproperties.load(data);
		log.info("set properties from file " + propertiesFile);
		s = (String)Myproperties.get(string);
	    }
	} catch (Exception e) {
	    log.info("cannot load properties from file " + propertiesFile + " : " + e);
	}
	return s;
    }
    
    /**
     * Writes the properties file containing user access key and secret 
     * @param key
     * @param secret
     * @throws IOException
     */
    private void writePropertiesFile(String key, String secret) throws IOException{
	File f = new File(System.getProperty("user.dir") + File.separator + propertiesFile);
	BufferedWriter bw = new BufferedWriter(new FileWriter(f));
	
	    bw.write("xtremweb.serv.dr.dropbox.token-key="+key+"\n");
	
	bw.write("xtremweb.serv.dr.dropbox.token-secret="+secret);
	bw.close();
	
    }

    /**
     * Returns transfer state
     */
    public boolean poolTransfer() {
	return !isTransfering();
    }

    /**
     * Disconnect from dropbox
     */
    public void disconnect() throws OOBException {
    }

    /**
     * Put a file using dropbox
     */
    public void blockingSendSenderSide() throws OOBException {
	String path = props.getProperty("xtremweb.serv.dr.dropbox.path")
		+ local_locator.getdatauid();
	File f = new File(local_locator.getref());
	FileInputStream fis= null;
	try {
	    fis = new FileInputStream(f);
	    api.putFile(path, fis, f.length(), null, null);
	} catch (FileNotFoundException e) {
	    e.printStackTrace();
	    throw new OOBException("The file was not found " + e.getMessage());

	} catch (DropboxException e) {
	    e.printStackTrace();
	    throw new OOBException(
		    "There was an exception using the DropBox API "
			    + e.getMessage());

	}finally{
	    try {
		if (fis!= null)
		    fis.close();
	    } catch (IOException e) {}
	}
    }

    /**
     * Empty method in dropbox case
     */
    public void blockingSendReceiverSide() throws OOBException {
	
    }

    /**
     * Empty method in dropbox case
     */
    public void blockingReceiveSenderSide() throws OOBException {
	
    }

    /**
     * Get file using dropbox
     */
    public void blockingReceiveReceiverSide() throws OOBException {
	String path = props.getProperty("xtremweb.serv.dr.dropbox.path")
		+ data.getuid();
	log.debug("local locator is " + local_locator.getref());
	log.debug(" remote loc is " + remote_locator.getref());
	try {
	    OutputStream fos = new FileOutputStream(new File(
		    local_locator.getref()));
	    api.getFile(path, null, fos, null);
	    fos.close();
	} catch (DropboxException e) {
	    e.printStackTrace();
	    throw new OOBException("There was a dropbox exception : "
		    + e.getMessage());
	} catch (FileNotFoundException e) {
	    e.printStackTrace();
	    throw new OOBException("File not found exception " + e.getMessage());
	} catch (IOException e) {
	    e.printStackTrace();
	    throw new OOBException("IO exception " + e.getMessage());
	}
    }

}
