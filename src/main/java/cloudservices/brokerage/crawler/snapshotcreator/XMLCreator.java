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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Arash Khodadadi <http://www.arashkhodadadi.com/>
 */
public class XMLCreator {

    private final static Logger LOGGER = Logger.getLogger(SnapshotCreator.class.getName());
    private final ServiceDescriptionSnapshotDAO snapshotDAO;
    private int totalNum;
    private int savedNum;
    private int unvalidNum;

    public XMLCreator() {
        this.snapshotDAO = new ServiceDescriptionSnapshotDAO();
    }

    public void generate(long startingId, long endingId, ServiceDescriptionType type, String xmlAddress, XMLStrategy strategy, String withCtxReposAddress, String withoutCtxReposAddress, String tempAddress, boolean createSeparate) throws DAOException, IOException {
        LOGGER.log(Level.INFO, "Creating XML between: {0} and {1} with Type : {2}", new Object[]{startingId, endingId, type});
        LOGGER.log(Level.INFO, "XML address: {0} and Strategy : {1}", new Object[]{xmlAddress, strategy});

        File xml = new File(xmlAddress);
        if (xml.exists()) {
            xml.delete();
            LOGGER.log(Level.SEVERE, "File removed");
        }

        List<ServiceDescriptionSnapshot> snapshots = getSnapshots(startingId, endingId, type, strategy);
        LOGGER.log(Level.INFO, "{0} Found", snapshots.size());
        totalNum = snapshots.size();

        FileWriter.writeInputStream(new FileInputStream("description.xml"), xml);
        LOGGER.log(Level.INFO, "description.xml successfully added to file");

        for (ServiceDescriptionSnapshot snapshot : snapshots) {
            LOGGER.log(Level.INFO, "Writing Snapshot with ID: {0}", snapshot.getId());
            if (validateSnapshot(snapshot, withCtxReposAddress, withoutCtxReposAddress, tempAddress, strategy, createSeparate)) {

                String fileElement = getXMLElement(snapshot, strategy);
                LOGGER.log(Level.FINE, "Writing {0}", fileElement);
                FileWriter.appendString(fileElement, xmlAddress);
                savedNum++;
            } else {
                LOGGER.log(Level.SEVERE, "Snapshot is invalid!");
                unvalidNum++;
            }
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
        sb.append("<file id=\"").append(snapshot.getId()).append("\" path=\"").append(snapshot.getFileAddress().replace(".html", ".txt")).append("\">");
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

    private boolean validateSnapshot(ServiceDescriptionSnapshot snapshot, String withCtxReposAddress, String withoutCtxReposAddress, String tempAddress, XMLStrategy strategy, boolean createSeparate) throws IOException {
        String fileAddress = snapshot.getFileAddress();

        if (snapshot.getType() == ServiceDescriptionType.REST) {
            fileAddress = fileAddress.replace(".html", ".txt");
        }
        File withCtxFile = new File(withCtxReposAddress.concat(fileAddress));
        File plainFile = new File(withoutCtxReposAddress.concat(fileAddress));
        if (!withCtxFile.exists() || withCtxFile.isDirectory()) {
            LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} does not have with context at {1}", new Object[]{snapshot.getId(), withCtxFile.getPath()});
            return false;
        }
        if (!plainFile.exists() || plainFile.isDirectory()) {
            LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} does not have without context at {1}", new Object[]{snapshot.getId(), plainFile.getPath()});
            return false;
        }

        String ctxTempDirAddress = tempAddress.concat("/WithContext/");
        if (createSeparate) {
            ctxTempDirAddress = ctxTempDirAddress.concat(getCVE(snapshot, strategy) + "/");
        }
        String tempCtxFileAddress = ctxTempDirAddress.concat(fileAddress);

        String plainTempDirAddress = tempAddress.concat("/WithoutContext/");
        if (createSeparate) {
            plainTempDirAddress = plainTempDirAddress.concat(getCVE(snapshot, strategy) + "/");
        }

        String tempPlainFileAddress = plainTempDirAddress.concat(fileAddress);

        FileUtils.copyFile(withCtxFile, new File(tempCtxFileAddress));
        FileUtils.copyFile(plainFile, new File(tempPlainFileAddress));

        if (snapshot.getType() == ServiceDescriptionType.REST) {
            fileAddress = fileAddress.replace(".txt", ".html");
            withCtxFile = new File(withCtxReposAddress.concat(fileAddress));
            plainFile = new File(withoutCtxReposAddress.concat(fileAddress));
            if (!withCtxFile.exists() || withCtxFile.isDirectory()) {
                LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} does not have with context at {1}", new Object[]{snapshot.getId(), withCtxFile.getPath()});
                return false;
            }
            if (!plainFile.exists() || plainFile.isDirectory()) {
                LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} does not have without context at {1}", new Object[]{snapshot.getId(), plainFile.getPath()});
                return false;
            }

            ctxTempDirAddress = tempAddress.concat("/WithContext/");
            if (createSeparate) {
                ctxTempDirAddress = ctxTempDirAddress.concat(getCVE(snapshot, strategy) + "/");
            }
            tempCtxFileAddress = ctxTempDirAddress.concat(fileAddress);

            plainTempDirAddress = tempAddress.concat("/WithoutContext/");
            if (createSeparate) {
                plainTempDirAddress = plainTempDirAddress.concat(getCVE(snapshot, strategy) + "/");
            }

            tempPlainFileAddress = plainTempDirAddress.concat(fileAddress);

            FileUtils.copyFile(withCtxFile, new File(tempCtxFileAddress));
            FileUtils.copyFile(plainFile, new File(tempPlainFileAddress));
        }

        createCtxFile(withCtxReposAddress, snapshot, ctxTempDirAddress);
        return true;
    }

    private void createCtxFile(String withCtxReposAddress, ServiceDescriptionSnapshot snapshot, String tempDirAddress) throws IOException {
        String context = getContextComments(new File(withCtxReposAddress.concat(snapshot.getFileAddress())));
        LOGGER.log(Level.FINER, "Context comments : {0} found", context);
        String newFilePath = null;
        switch (snapshot.getType()) {
            case REST:
                newFilePath = tempDirAddress.concat(snapshot.getFileAddress().replace(".html", ".ctx"));
                break;
            case WADL:
                newFilePath = tempDirAddress.concat(snapshot.getFileAddress().replace(".wadl", ".ctx"));
                break;
            case WSDL:
                newFilePath = tempDirAddress.concat(snapshot.getFileAddress().replace(".wsdl", ".ctx"));
                break;
        }

        File newFile = new File(newFilePath);
        if (newFile.exists()) {
            LOGGER.log(Level.INFO, "File already exists in {0}", newFilePath);
            newFile.delete();
        }

        LOGGER.log(Level.FINE, "Writing new file in {0}", newFilePath);
        FileWriter.appendString(context, newFilePath);
    }

    private String getContextComments(File file) {
        String context = "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            int counter = 1;
            String line = br.readLine();
            if (line == null || line.compareTo("<!--") != 0) {
                LOGGER.log(Level.SEVERE, "File {0} is not starting with comments!", file.getPath());
            } else {
                while ((line = br.readLine()) != null) {
                    context = context.concat(line).concat("\n");
                    counter++;
                    if (counter == 5) {
                        break;
                    }
                }
            }
            br.close();

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "File " + file.getPath() + " does not have well-formed context comments", ex);
        }
        return context;
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

    public int getUnvalidNum() {
        return unvalidNum;
    }

    public void setUnvalidNum(int unvalidNum) {
        this.unvalidNum = unvalidNum;
    }
}
