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
    private final static String CTX_REPOS_ADDRESS = "SnapshotRepository/WithContext/RESTS/";
    private final static String PLAIN_REPOS_ADDRESS = "SnapshotRepository/WithoutContext/RESTS/";
    private final static long STARTING_SNAPSHOT_ID = -1;
    private final static long STARTING_SERVICE_ID = 0;
    private final static long ENDING_SERVICE_ID = 100000;
    private final static ServiceDescriptionType DESCRIPTION_TYPE = ServiceDescriptionType.WADL;
    private final static SnapshotStrategy STRATEGY = SnapshotStrategy.NEW;

    private final static long STARTING_SNAPSHOT_ID_XML = 0;
    private final static long ENDING_SNAPSHOT_ID_XML = 1000000;
    private final static ServiceDescriptionType DESCRIPTION_TYPE_XML = ServiceDescriptionType.REST;
    private final static XMLStrategy XML_STRATEGY = XMLStrategy.PLAIN_CLASSIFIED;
    private final static String XML_ADDRESS = "test-RESTS.xml";
    private final static String TEMP_ADDRESS="test-RESTS/";
    
    private final static String HTML_REPOS_ADDRESS = "SnapshotRepository/WithoutContext/RESTS/";

    public static void main(String[] args) {
        createLogFile();
        //        createNewDB();

//        createSnapshots();
        createXML();
//        importSnapshots();
//        removeNoise();

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

        DirectoryUtil.createDirs(CTX_REPOS_ADDRESS);
        DirectoryUtil.createDirs(PLAIN_REPOS_ADDRESS);

        SnapshotCreator creator = new SnapshotCreator(USER_AGENTS, POLITENESS_DELAY);

        try {
            creator.CreateSnapshots(DESCRIPTION_TYPE, STRATEGY, CTX_REPOS_ADDRESS, PLAIN_REPOS_ADDRESS, STARTING_SNAPSHOT_ID, STARTING_SERVICE_ID, ENDING_SERVICE_ID);
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
            xmlCreator.generate(STARTING_SNAPSHOT_ID_XML, ENDING_SNAPSHOT_ID_XML, DESCRIPTION_TYPE_XML, XML_ADDRESS, XML_STRATEGY, CTX_REPOS_ADDRESS, PLAIN_REPOS_ADDRESS,TEMP_ADDRESS);
        } catch (DAOException | IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            BaseDAO.closeSession();
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            LOGGER.log(Level.SEVERE, "Creating XML End in {0}ms", totalTime);
            LOGGER.log(Level.SEVERE, "Total Snapshots found : {0}", xmlCreator.getTotalNum());
            LOGGER.log(Level.SEVERE, "Total Snapshots saved : {0}", xmlCreator.getSavedNum());
            LOGGER.log(Level.SEVERE, "Total Unvalid Snapshots : {0}", xmlCreator.getUnvalidNum());
        }
    }

    private static void importSnapshots() {
        long startTime = System.currentTimeMillis();
        LOGGER.log(Level.SEVERE, "Importing Snapshots Start");

        Configuration configuration = new Configuration();
        configuration.configure("v3hibernate.cfg.xml");
        Configuration tempConfig = new Configuration();
        tempConfig.configure("v3_temp_hibernate.cfg.xml");

        SnapshotImporter importer = new SnapshotImporter();

        try {
            importer.Import(tempConfig, configuration, ServiceDescriptionType.WSDL, 0L, 1000000L);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            LOGGER.log(Level.SEVERE, "Importing Snapshots End in {0}ms", totalTime);
            LOGGER.log(Level.SEVERE, "Total Snapshots Found : {0}", importer.getTempSnapshotsNum());
            LOGGER.log(Level.SEVERE, "Total Snapshots Saved : {0}", importer.getSavedSnapshotsNum());
            LOGGER.log(Level.SEVERE, "Total Services Found: {0}", importer.getTempServicesNum());
            LOGGER.log(Level.SEVERE, "Total Services Updated: {0}", importer.getUpdatedServicesNum());
        }
    }

    private static void removeNoise() {
        long startTime = System.currentTimeMillis();
        LOGGER.log(Level.SEVERE, "Noise Removal Start");

        HTMLNoiseRemover noiseRemover = new HTMLNoiseRemover();

        try {
            noiseRemover.removeNoise(HTML_REPOS_ADDRESS,false);
        } finally {
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            LOGGER.log(Level.SEVERE, "Noise Removal End in {0}ms", totalTime);
            LOGGER.log(Level.SEVERE, "Total HTML files found : {0}", noiseRemover.getTotalNum());
            LOGGER.log(Level.SEVERE, "Total HTML files saved : {0}", noiseRemover.getSavedNum());
            LOGGER.log(Level.SEVERE, "Total Unvalid HTML files : {0}", noiseRemover.getUnvalidNum());
        }
    }
}
