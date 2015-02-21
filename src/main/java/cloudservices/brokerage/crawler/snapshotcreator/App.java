/*
 * Copyright 2014 Arash khodadadi.
 * <http://www.arashkhodadadi.com/>
 */
package cloudservices.brokerage.crawler.snapshotcreator;

import cloudservices.brokerage.commons.utils.file_utils.DirectoryUtil;
import cloudservices.brokerage.commons.utils.logging.LoggerSetup;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.BaseDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.DAOException;
import cloudservices.brokerage.crawler.crawlingcommons.model.enums.v3.ServiceDescriptionType;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import org.hibernate.cfg.Configuration;

/**
 *
 * @author Arash Khodadadi <http://www.arashkhodadadi.com/>
 */
public class App {

    private final static Logger LOGGER = Logger.getLogger(App.class.getName());
    private final static String[] USER_AGENTS = new String[]{
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2227.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2227.1 Safari/537.36",
        "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2224.3 Safari/537.36", //        "Mozilla/5.0 (X11; OpenBSD i386) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/36.0.1985.125 Safari/537.36",
    //        "Mozilla/5.0 (Windows NT 6.3; rv:36.0) Gecko/20100101 Firefox/36.0",
    //        "Mozilla/5.0 (Windows NT 5.1; rv:15.0) Gecko/20100101 Firefox/13.0.1",
    //        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:9.0a2) Gecko/20111101 Firefox/9.0a2",
    //        "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; AS; rv:11.0) like Gecko",
    //        "Mozilla/5.0 (compatible; MSIE 10.6; Windows NT 6.1; Trident/5.0; InfoPath.2; SLCC1; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729; .NET CLR 2.0.50727) 3gpp-gba UNTRUSTED/1.0",
    //        "Mozilla/5.0 (Windows NT 6.0; rv:14.0) Gecko/20100101 Firefox/14.0.1",
    //        "Mozilla/5.0 (X11; Mageia; Linux x86_64; rv:10.0.9) Gecko/20100101 Firefox/10.0.9"
    };
    private final static long POLITENESS_DELAY = 100; //ms
    private final static String WSDL_CTX_ADDRESS = "SnapshotRepository/WithContext/WADLS/";
    private final static String WSDL_PLAIN_ADDRESS = "SnapshotRepository/WithoutContext/WADLS/";
    private final static long STARTING_SNAPSHOT_ID = 300000;
    private final static long STARTING_SERVICE_ID = 0;
    private final static long ENDING_SERVICE_ID = 100000;
    private final static ServiceDescriptionType DESCRIPTION_TYPE = ServiceDescriptionType.WADL;
    private final static SnapshotStrategy STRATEGY = SnapshotStrategy.NEW;

    private final static long STARTING_SNAPSHOT_ID_XML = 0;
    private final static long ENDING_SNAPSHOT_ID_XML = 1000000;
    private final static ServiceDescriptionType DESCRIPTION_TYPE_XML = ServiceDescriptionType.WADL;
    private final static XMLStrategy XML_STRATEGY = XMLStrategy.CONTEXT_CLASSIFIED;
    private final static String XML_ADDRESS = "generated.xml";

    public static void main(String[] args) {
        createLogFile();
        //        createNewDB();

//        createSnapshots();
        createXML();

        System.exit(0);
    }

    private static boolean createLogFile() {
        try {
            StringBuilder sb = new StringBuilder();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm");
            Calendar cal = Calendar.getInstance();
            sb.append(dateFormat.format(cal.getTime()));
            String filename = sb.toString();
            DirectoryUtil.createDir("logs");
            LoggerSetup.setup("logs/" + filename + ".txt", "logs/" + filename + ".html", Level.INFO);
            return true;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        }
    }

    private static void createSnapshots() {
        long startTime = System.currentTimeMillis();
        LOGGER.log(Level.SEVERE, "Snapping Start");

        Configuration configuration = new Configuration();
        configuration.configure("v3hibernate.cfg.xml");
        BaseDAO.openSession(configuration);

        DirectoryUtil.createDirs(WSDL_CTX_ADDRESS);
        DirectoryUtil.createDirs(WSDL_PLAIN_ADDRESS);

        SnapshotCreator creator = new SnapshotCreator(USER_AGENTS, POLITENESS_DELAY);

        try {
            creator.CreateSnapshots(DESCRIPTION_TYPE, STRATEGY, WSDL_CTX_ADDRESS, WSDL_PLAIN_ADDRESS, STARTING_SNAPSHOT_ID, STARTING_SERVICE_ID, ENDING_SERVICE_ID);
        } catch (DAOException | IOException | XMLStreamException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            BaseDAO.closeSession();
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            LOGGER.log(Level.SEVERE, "Snapping End in {0}ms", totalTime);
            LOGGER.log(Level.SEVERE, "Total Descriptions for Snapping : {0}", creator.getTotalDescNum());
            LOGGER.log(Level.SEVERE, "With Error Response : {0}", creator.getNoResponse());
            LOGGER.log(Level.SEVERE, "With Not Validated Response : {0}", creator.getNotValidReponse());
            LOGGER.log(Level.SEVERE, "Already in Snapshots : {0}", creator.getSame());
            LOGGER.log(Level.SEVERE, "Saved Snapshots : {0}", creator.getSavedSnapshots());
            LOGGER.log(Level.SEVERE, "Undefined Providers : {0}", creator.getUndefinedProviders());
        }
    }

    private static void createXML() {
        long startTime = System.currentTimeMillis();
        LOGGER.log(Level.SEVERE, "Creating XML Start");

        Configuration configuration = new Configuration();
        configuration.configure("v3hibernate.cfg.xml");
        BaseDAO.openSession(configuration);

        XMLCreator xmlCreator = new XMLCreator();

        try {
            xmlCreator.generate(STARTING_SNAPSHOT_ID_XML, ENDING_SNAPSHOT_ID_XML, DESCRIPTION_TYPE_XML, XML_ADDRESS, XML_STRATEGY);
        } catch (DAOException | IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            BaseDAO.closeSession();
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            LOGGER.log(Level.SEVERE, "Creating XML End in {0}ms", totalTime);
            LOGGER.log(Level.SEVERE, "Total Snapshots found : {0}", xmlCreator.getTotalNum());
            LOGGER.log(Level.SEVERE, "Total Snapshots saved : {0}", xmlCreator.getSavedNum());
        }
    }
}
