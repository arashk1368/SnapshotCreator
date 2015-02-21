/*
 * Copyright 2014 Arash khodadadi.
 * <http://www.arashkhodadadi.com/>
 */
package cloudservices.brokerage.crawler.snapshotcreator;

import cloudservices.brokerage.commons.utils.file_utils.FileWriter;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.DAOException;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.v3.ServiceDescriptionSnapshotDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.entities.v3.ServiceDescriptionSnapshot;
import cloudservices.brokerage.crawler.crawlingcommons.model.enums.v3.ServiceDescriptionType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Arash Khodadadi <http://www.arashkhodadadi.com/>
 */
public class XMLCreator {

    private final static Logger LOGGER = Logger.getLogger(SnapshotCreator.class.getName());
    private final ServiceDescriptionSnapshotDAO snapshotDAO;
    private int totalNum;
    private int savedNum;

    public XMLCreator() {
        this.snapshotDAO = new ServiceDescriptionSnapshotDAO();
    }

    public void generate(long startingId, long endingId, ServiceDescriptionType type, String xmlAddress, XMLStrategy strategy) throws DAOException, IOException {
        LOGGER.log(Level.INFO, "Creating XML between: {0} and {1} with Type : {2}", new Object[]{startingId, endingId, type});
        LOGGER.log(Level.INFO, "XML address: {0} and Strategy : {1}", new Object[]{xmlAddress, strategy});

        File xml = new File(xmlAddress);
        if (xml.exists() || xml.isDirectory()) {
            LOGGER.log(Level.SEVERE, "File is not valid");
            return;
        }

        List<ServiceDescriptionSnapshot> snapshots = getSnapshots(startingId, endingId, type, strategy);
        LOGGER.log(Level.INFO, "{0} Found", snapshots.size());
        totalNum = snapshots.size();

        FileWriter.writeInputStream(new FileInputStream("description.xml"), xml);
        LOGGER.log(Level.INFO, "description.xml successfully added to file");

        for (ServiceDescriptionSnapshot snapshot : snapshots) {
            LOGGER.log(Level.INFO, "Writing Snapshot with ID: {0}", snapshot.getId());
            String fileElement = getXMLElement(snapshot, strategy);
            LOGGER.log(Level.FINE, "Writing {0}", fileElement);
            FileWriter.appendString(fileElement, xmlAddress);
            savedNum++;
        }

        FileWriter.appendString("</dataset>", xmlAddress);
    }

    private List<ServiceDescriptionSnapshot> getSnapshots(long startingId, long endingId, ServiceDescriptionType type, XMLStrategy strategy) throws DAOException {
        switch (strategy) {
            case ALL:
                return this.snapshotDAO.getAll(startingId, endingId, type);
            case CONTEXT_CLASSIFIED:
                return this.snapshotDAO.getContexClassified(startingId, endingId, type);
            case NOT_CLASSIFIED:
                return this.snapshotDAO.getNotClassified(startingId, endingId, type);
            case PLAIN_CLASSIFIED:
                return this.snapshotDAO.getPlainClassified(startingId, endingId, type);
        }
        return new ArrayList<>();
    }

    private String getXMLElement(ServiceDescriptionSnapshot snapshot, XMLStrategy strategy) {
        StringBuilder sb = new StringBuilder();
        sb.append("<file id=\"").append(snapshot.getId()).append("\" path=\"").append(snapshot.getFileAddress()).append("\">");
        sb.append("\n");
        sb.append("	<meta>\n"
                + "		<type></type>\n"
                + "		<length lines=\"\" words=\"\" bytes=\"\" />\n"
                + "	</meta>\n"
                + "	<location line=\"\" fraglines=\"\">\n"
                + "		<meta>\n");
        sb.append("			<cve>");
        sb.append(getCVE(snapshot, strategy));
        sb.append("</cve>\n");
        sb.append("			<name cweid=\"\"></name>\n"
                + "		</meta>\n"
                + "		<fragment>\n"
                + "		</fragment>\n"
                + "		<explanation>\n"
                + "		</explanation>\n"
                + "	</location>\n"
                + "</file>\n");
        return sb.toString();
    }

    private String getCVE(ServiceDescriptionSnapshot snapshot, XMLStrategy strategy) {
        switch (strategy) {
            case CONTEXT_CLASSIFIED:
                return snapshot.getPrimaryCategoryWithCtx().getName();
            case PLAIN_CLASSIFIED:
                return snapshot.getPrimaryCategoryPlain().getName();
        }
        return "";
    }

    public int getTotalNum() {
        return totalNum;
    }

    public void setTotalNum(int totalNum) {
        this.totalNum = totalNum;
    }

    public int getSavedNum() {
        return savedNum;
    }

    public void setSavedNum(int savedNum) {
        this.savedNum = savedNum;
    }

}
