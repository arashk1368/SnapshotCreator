/*
 * Copyright 2014 Arash khodadadi.
 * <http://www.arashkhodadadi.com/>
 */
package cloudservices.brokerage.crawler.snapshotcreator;

import cloudservices.brokerage.commons.utils.file_utils.DirectoryUtil;
import cloudservices.brokerage.commons.utils.file_utils.FileWriter;
import cloudservices.brokerage.commons.utils.logging.LoggerSetup;
import cloudservices.brokerage.commons.utils.url_utils.URLRequester;
import cloudservices.brokerage.commons.utils.validators.WSDLValidator;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.BaseDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.DAOException;
import cloudservices.brokerage.crawler.crawlingcommons.model.enums.v3.ServiceDescriptionType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
        "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2224.3 Safari/537.36",
//        "Mozilla/5.0 (X11; OpenBSD i386) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/36.0.1985.125 Safari/537.36",
//        "Mozilla/5.0 (Windows NT 6.3; rv:36.0) Gecko/20100101 Firefox/36.0",
//        "Mozilla/5.0 (Windows NT 5.1; rv:15.0) Gecko/20100101 Firefox/13.0.1",
//        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:9.0a2) Gecko/20111101 Firefox/9.0a2",
//        "Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; AS; rv:11.0) like Gecko",
//        "Mozilla/5.0 (compatible; MSIE 10.6; Windows NT 6.1; Trident/5.0; InfoPath.2; SLCC1; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729; .NET CLR 2.0.50727) 3gpp-gba UNTRUSTED/1.0",
//        "Mozilla/5.0 (Windows NT 6.0; rv:14.0) Gecko/20100101 Firefox/14.0.1",
//        "Mozilla/5.0 (X11; Mageia; Linux x86_64; rv:10.0.9) Gecko/20100101 Firefox/10.0.9"
    };
    private final static long POLITENESS_DELAY = 1000; //ms
    private final static String WSDL_CTX_ADDRESS = "SnapshotRepository/WithContext/WSDLS/";
    private final static String WSDL_PLAIN_ADDRESS = "SnapshotRepository/WithoutContext/WSDLS/";

    public static void main(String[] args) {
        createLogFile();
        //        createNewDB();
        long startTime = System.currentTimeMillis();
        LOGGER.log(Level.SEVERE, "Snapping Start");

        Configuration configuration = new Configuration();
        configuration.configure("v3hibernate.cfg.xml");
        BaseDAO.openSession(configuration);

        DirectoryUtil.createDirs(WSDL_CTX_ADDRESS);
        DirectoryUtil.createDirs(WSDL_PLAIN_ADDRESS);

        SnapshotCreator creator = new SnapshotCreator(USER_AGENTS, POLITENESS_DELAY);

        try {
            creator.CreateSnapshots(ServiceDescriptionType.WSDL, SnapshotStrategy.NEW, WSDL_CTX_ADDRESS, WSDL_PLAIN_ADDRESS);
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
            System.exit(0);
        }
    }

    private static boolean createLogFile() {
        try {
            StringBuilder sb = new StringBuilder();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm");
            Calendar cal = Calendar.getInstance();
            sb.append(dateFormat.format(cal.getTime()));
            String filename = sb.toString();
            DirectoryUtil.createDir("logs");
            LoggerSetup.setup("logs/" + filename + ".txt", "logs/" + filename + ".html", Level.FINER);
            return true;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
            return false;
        }
    }
}
