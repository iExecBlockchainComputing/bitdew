package xtremweb.role.examples;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.BrokenBarrierException;
import java.util.Vector;
import jargs.gnu.CmdLineParser;

import xtremweb.core.log.*;
import xtremweb.core.serv.*;
import xtremweb.core.conf.*;
import xtremweb.serv.ds.*;
import xtremweb.serv.dc.DataUtil;
import xtremweb.core.com.idl.*;
import xtremweb.core.iface.*;

import xtremweb.api.bitdew.*;
import xtremweb.api.activedata.*;
import xtremweb.api.transman.*;
import xtremweb.core.obj.dc.*;
import xtremweb.core.obj.ds.*;
import xtremweb.role.ui.*;
import xtremweb.serv.bench.*;


//Master java -cp conf:bitdew-stand-alone-0.0.2-sc08.jar  xtremweb.role.examples.AllToAll --master --port=4325 --workers=2 --replicat=1 --data=2 --dir=/home/ftp/pub/incoming/ --oob=ftp

//Worker 

public class AllToAll {

    String oob;
    Logger log = LoggerFactory.getLogger(AllToAll.class);
    BitDew bitdew = null;
    ActiveData activeData = null;
    TransferManager transferManager = null;
    String _dir;

    InterfaceRMIbench ibench=null;
    long myrank;
    String hostName = "nonhostname";
    
    int replicat;
    int nbdata;
    int workers;
    int received=0;
    int size;
    long start;
    long dataScheduled;
    long end;

    CollectiveData[] dataArray;

    final int FILE_SIZE = 5000;

    public AllToAll(String host, int port, boolean master, String dir, String p, int w, String myId , int s, int d, int r, String mh) throws Exception {

	workers = w;
	_dir=dir;
	replicat=r;
	nbdata=d;
	oob=p;
	size=s;
	log.info("worker=" + workers + " nbdata=" + nbdata + "replicat=" + replicat );
	Host myHost =  ComWorld.getHost();


	if (!mh.equals("localhost")) {
	    hostName = mh;
	} else {
	    try	 {
		hostName = java.net.InetAddress.getLocalHost().getHostName();
	    } catch(Exception e ){
		log.fatal("WTF " + e);
	    }
	}
	log.info("my Host " + myHost.getuid() + " hostname: " + hostName);
	//master initialisation : loads the service
	if (master) {
	    String[] modules = {"dc","dr","dt","ds"};
	    ServiceLoader sl = new ServiceLoader("RMI", port, modules);
	    CallbackAllToAllBench cbbench = new CallbackAllToAllBench();
	    sl.addSubCallback("RMI", port, "bench", cbbench); 
	    cbbench.configure(workers + 1, 2);
	    log.info("bench installed");
	}

	//intialise the communication and the APIs
	Vector comms = ComWorld.getMultipleComms(host, "rmi", port, "dc", "dr", "dt", "ds");

	bitdew = new BitDew(comms);

	activeData = new ActiveData(comms);

	ibench = (InterfaceRMIbench) ComWorld.getComm( host, "rmi", port, "bench" );
	myrank = ibench.register(hostName);
	log.info( hostName +  "rank is " + myrank );

	if (master) {
	    dataArray = new CollectiveData[nbdata];	    
	    for (int i=0; i< nbdata; i++) {
		dataArray[i] = new CollectiveData(i);
	    }
	    
	    //phase 2
	    ibench.startExperience();
	    ibench.endExperience(myrank,0,null);
	    log.info("Entering phase2");
	    //ExclIl faut soumattr1
	    for (int i=0; i< nbdata; i++) {
		AttributeType.setAttributeTypeOn( dataArray[i].attr, AttributeType.AFFINITY );
		if (i<(nbdata-1))
		    dataArray[i].attr.setaffinity(dataArray[i+1].data.getuid());
		else
		    dataArray[i].attr.setaffinity(dataArray[0].data.getuid());
		activeData.registerAttribute(dataArray[i].attr);
		log.debug("scheduling attribute " + AttributeUtil.toString(dataArray[i].attr) + " data  " + DataUtil.toString(dataArray[i].data));
	    }
	    ibench.startExperience();
	    //phase 2
	    Thread.sleep(5000);
	    ibench.endExperience(myrank,0,null);

	    //exit safely
	    ibench.startExperience();
	} else {

	    //code for the client
	    transferManager = TransferManagerFactory.getTransferManager(comms);
	    activeData.registerActiveDataCallback(new BroadcastCallback());

	    transferManager.start();
	    ibench.startExperience();
	    activeData.start();

	}
    }
    

    public void createFic(File fic, int taille) {
	if (!fic.exists()) {
	   
	    byte[] buffer = new byte[1024];
	    //buffer is filled with random bits
	    for (int i=0; i< 1024; i++) {
		buffer[i]=(byte) i;
	    }
	    try {
		FileOutputStream fos = new FileOutputStream( fic );
		// buffer is copied to the file
		for (int i = 0; i< taille; i++) {
		    fos.write(buffer);
		}
	    } catch (Exception e){ 
		log.fatal("Cannot create benchmarked file " + e);
		System.exit(0);
	    }
	}
    }

    public class CollectiveData  {
	Data data;
	Locator locator;
	Attribute attr;
	File file;

	public CollectiveData (int idx ) throws Exception {	    
	    file = new File(_dir,"data" + idx);
	    createFic(file, size);
	    data = bitdew.createData(file);
	    data.setoob(oob);
	    locator = bitdew.createLocator("data" + idx);
	    bitdew.put(data,locator);
	    attr = activeData.createAttribute("attr attr" + idx + " = {replicat = " + replicat + ", oob = " + oob + "  }");
	    activeData.schedule(data, attr);
	    log.debug("scheduling attribute " + AttributeUtil.toString(attr) + " data  " + DataUtil.toString(data));
	}
    }


    public static void main(String[] args) throws Exception {
	CmdLineParser parser = new CmdLineParser();
	CmdLineParser.Option helpOption = parser.addBooleanOption('h', "help");
	CmdLineParser.Option portOption = parser.addIntegerOption("port");
	CmdLineParser.Option replicatOption = parser.addIntegerOption("replicat");
	CmdLineParser.Option sizeOption = parser.addIntegerOption("size");
	CmdLineParser.Option dataOption = parser.addIntegerOption("data");
	CmdLineParser.Option workersOption = parser.addIntegerOption("workers");
	CmdLineParser.Option hostOption = parser.addStringOption("host");
	CmdLineParser.Option myIdOption = parser.addStringOption("myId");
	CmdLineParser.Option dirOption = parser.addStringOption("dir");
	CmdLineParser.Option masterOption = parser.addBooleanOption("master");
	CmdLineParser.Option oobOption = parser.addStringOption("oob");
	CmdLineParser.Option myHostOption = parser.addStringOption("myHost");

        try {
            parser.parse(args);
        }
        catch ( CmdLineParser.OptionException e ) {
            System.out.println(e.getMessage());
        }

    	boolean help = ((Boolean)parser.getOptionValue(helpOption, Boolean.FALSE)).booleanValue();
    	String host = (String) parser.getOptionValue(hostOption,"localhost");
    	String myId = (String) parser.getOptionValue(myIdOption);
    	String dir = (String) parser.getOptionValue(dirOption,"/tmp/pub/incoming");
    	String oob = (String) parser.getOptionValue(oobOption,"dummy");
   	int port = ((Integer) parser.getOptionValue(portOption,new Integer(4322))).intValue();
   	int workers = ((Integer) parser.getOptionValue(workersOption,new Integer(1))).intValue();
   	int replicat = ((Integer) parser.getOptionValue(replicatOption,new Integer(1))).intValue();
   	int size = ((Integer) parser.getOptionValue(sizeOption,new Integer(5000))).intValue();
   	int data = ((Integer) parser.getOptionValue(dataOption,new Integer(1))).intValue();
	boolean master = ((Boolean)parser.getOptionValue(masterOption, Boolean.FALSE)).booleanValue();
    	String myHost = (String) parser.getOptionValue(myHostOption,"localhost");

	if (help) {
	    System.exit(2);
	}

	if (master) host="localhost";
	else 
	    System.out.println("worker");

	try {
	    AllToAll bc = new AllToAll(host, port, master, dir, oob, workers, myId, size, data, replicat, myHost);
	} catch (Exception e) {
	     System.out.println(e.getMessage());
	}
    }
    /*
    public class AtaThread extends Thread() {

	
    }*/

    public class BroadcastCallback implements ActiveDataCallback {


	public void onDataScheduled(Data data, Attribute attr) {
	    try {

		if (received==0) {
			log.info("debut de la phase 1");
			start=System.currentTimeMillis();
		}

		log.info("data scheduled  " + data.getname() + " " + attr.getname());
		File fic = new File(_dir,"test" + received);
		bitdew.get(data, fic); 

		if (received==0) {
		    transferManager.waitFor(data);
		    end=System.currentTimeMillis();
		    log.info("transfer finished " + hostName + " " + (end-start) + " " + received  );
		    ibench.endExperience(myrank,end-start,null);
		    log.info("entering phase 2");		    
		    ibench.startExperience();
		    start=System.currentTimeMillis();
		}

		received++;
		//		    log.info("transfer finished " + hostName + " " + (end-start) + " " + received  );
		if (received==nbdata) {
		    //		    transferManager.waitFor(data);
		    transferManager.waitForAllData();
		    end=System.currentTimeMillis();
		    log.info("transfer finished " + hostName + " " + (end-start) + " " + received  );
		    ibench.endExperience(myrank,end-start,null);
		    log.info("end of phase2");
		    ibench.startExperience();
		    System.exit(0);
		}

	    } catch (Exception e) {
		log.warn("finish with error " + e);
		System.exit(0);
	    }
	}

	public void onDataDeleted(Data data, Attribute attr) {}
    }


}
