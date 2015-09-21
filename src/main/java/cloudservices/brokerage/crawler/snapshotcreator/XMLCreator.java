/*
 * Copyright 2014 Arash khodadadi.
 * <http://www.arashkhodadadi.com/>
 */
package cloudservices.brokerage.crawler.snapshotcreator;

import cloudservices.brokerage.commons.utils.file_utils.FileWriter;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.DAOException;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.v3.ServiceDescriptionSnapshotDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.entities.v3.Category;
import cloudservices.brokerage.crawler.crawlingcommons.model.entities.v3.ServiceDescriptionSnapshot;
import cloudservices.brokerage.crawler.crawlingcommons.model.enums.v3.ServiceDescriptionType;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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

    public void generate(long startingId, long endingId, ServiceDescriptionType type, String xmlAddress, XMLStrategy strategy, String withCtxReposAddress, String withoutCtxReposAddress, String folderPrefix, boolean copy, String tempAddress, boolean createSeparate) throws DAOException, IOException {
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

        WriteSnapshots(snapshots, withCtxReposAddress, withoutCtxReposAddress, folderPrefix, tempAddress, strategy, copy, createSeparate, xmlAddress);

        FileWriter.appendString("</dataset>", xmlAddress);
    }

    public void generate(ServiceDescriptionType type, XMLStrategy strategy, double testingPercentage, String withCtxReposAddress, String withoutCtxReposAddress, String trainFolderPrefix, String testFolderPrefix, String tempAddress, boolean copy, boolean createSeparate) throws IOException, DAOException {
        LOGGER.log(Level.INFO, "Creating XML for Type : {0}, Strategy : {1}, and Testing Percentage: {2}", new Object[]{type, strategy, testingPercentage});

        String trainXMLAddress = "train-" + type.toString() + "S.xml";
        String testXMLAddress = "test-" + type.toString() + "S.xml";
        File trainXML = new File(trainXMLAddress);
        File testXML = new File(testXMLAddress);
        if (trainXML.exists()) {
            trainXML.delete();
            LOGGER.log(Level.SEVERE, "Train File removed");
        }
        if (testXML.exists()) {
            testXML.delete();
            LOGGER.log(Level.SEVERE, "Test File removed");
        }

        List<ServiceDescriptionSnapshot> snapshots = getSnapshots(0, 1000000, type, strategy);
        LOGGER.log(Level.INFO, "{0} Found", snapshots.size());
        totalNum = snapshots.size();

        FileWriter.writeInputStream(new FileInputStream("description.xml"), trainXML);
        FileWriter.writeInputStream(new FileInputStream("description.xml"), testXML);
        LOGGER.log(Level.INFO, "description.xml successfully added to files");

        HashMap<Category, List<ServiceDescriptionSnapshot>> trainingSet = new HashMap<>();
        HashMap<Category, List<ServiceDescriptionSnapshot>> testingSet = new HashMap<>();
        distributeSnapshots(snapshots, strategy, testingPercentage, trainingSet, testingSet);

        for (Map.Entry<Category, List<ServiceDescriptionSnapshot>> entrySet : trainingSet.entrySet()) {
            Category category = entrySet.getKey();
            List<ServiceDescriptionSnapshot> cluster = entrySet.getValue();
            LOGGER.log(Level.INFO, "Writing training set for category: {0}", category.getName());
            WriteSnapshots(cluster, withCtxReposAddress, withoutCtxReposAddress, trainFolderPrefix, tempAddress, strategy, copy, createSeparate, trainXMLAddress);
        }

        for (Map.Entry<Category, List<ServiceDescriptionSnapshot>> entrySet : testingSet.entrySet()) {
            Category category = entrySet.getKey();
            List<ServiceDescriptionSnapshot> cluster = entrySet.getValue();
            LOGGER.log(Level.INFO, "Writing testing set for category: {0}", category.getName());
            WriteSnapshots(cluster, withCtxReposAddress, withoutCtxReposAddress, testFolderPrefix, tempAddress, strategy, copy, createSeparate, testXMLAddress);
        }

        FileWriter.appendString("</dataset>", trainXMLAddress);
        FileWriter.appendString("</dataset>", testXMLAddress);
    }

    public void generate10Fold(ServiceDescriptionType type, XMLStrategy strategy, String withCtxReposAddress, String withoutCtxReposAddress, String trainFolderPrefix, String testFolderPrefix, boolean copy, String tempAddress, boolean createSeparate) throws IOException, DAOException {
        LOGGER.log(Level.INFO, "Creating 10-fold XMLs for Type : {0}, Strategy : {1}", new Object[]{type, strategy});

        List<ServiceDescriptionSnapshot> snapshots = getSnapshots(0, 1000000, type, strategy);
        LOGGER.log(Level.INFO, "{0} Found", snapshots.size());
        totalNum = snapshots.size();

        HashMap<Category, List<List<ServiceDescriptionSnapshot>>> testingMap = Create10FoldSets(snapshots, strategy);

        for (int i = 0; i < 10; i++) {
            LOGGER.log(Level.INFO, "Fold {0}", i);
            String trainXMLAddress = "train-" + type.toString() + "S" + i + ".xml";
            String testXMLAddress = "test-" + type.toString() + "S" + i + ".xml";
            File trainXML = new File(trainXMLAddress);
            File testXML = new File(testXMLAddress);
            if (trainXML.exists()) {
                trainXML.delete();
                LOGGER.log(Level.SEVERE, "Train File : {0} removed", trainXMLAddress);
            }
            if (testXML.exists()) {
                testXML.delete();
                LOGGER.log(Level.SEVERE, "Test File : {0} removed", testXMLAddress);
            }

            FileWriter.writeInputStream(new FileInputStream("description.xml"), trainXML);
            FileWriter.writeInputStream(new FileInputStream("description.xml"), testXML);
            LOGGER.log(Level.INFO, "description.xml successfully added to files");

            for (Map.Entry<Category, List<List<ServiceDescriptionSnapshot>>> entrySet : testingMap.entrySet()) {
                Category category = entrySet.getKey();
                List<List<ServiceDescriptionSnapshot>> testingSets = entrySet.getValue();
                LOGGER.log(Level.INFO, "Writing testing set for category: {0}", category.getName());
                WriteSnapshots(testingSets.get(i), withCtxReposAddress, withoutCtxReposAddress, testFolderPrefix, tempAddress, strategy, copy, createSeparate, testXMLAddress);

                List<ServiceDescriptionSnapshot> training = new ArrayList<>();
                for (int j = 0; j < 10; j++) {
                    if (j == i) {
                        continue;
                    }
                    training.addAll(testingSets.get(j));
                }
                LOGGER.log(Level.INFO, "Writing training set for category: {0}", category.getName());
                WriteSnapshots(training, withCtxReposAddress, withoutCtxReposAddress, trainFolderPrefix, tempAddress, strategy, copy, createSeparate, trainXMLAddress);
            }

            FileWriter.appendString("</dataset>", trainXMLAddress);
            FileWriter.appendString("</dataset>", testXMLAddress);
        }
    }

    private void WriteSnapshots(List<ServiceDescriptionSnapshot> snapshots, String withCtxReposAddress, String withoutCtxReposAddress, String folderPrefix, String tempAddress, XMLStrategy strategy, boolean copy, boolean createSeparate, String xmlFileAddress) throws IOException {
        for (ServiceDescriptionSnapshot snapshot : snapshots) {
            LOGGER.log(Level.INFO, "Writing Snapshot with ID: {0}", snapshot.getId());
            boolean valid = copy ? validateSnapshot(snapshot, withCtxReposAddress, withoutCtxReposAddress, tempAddress, strategy, createSeparate)
                    : validateSnapshot(snapshot, withCtxReposAddress, withoutCtxReposAddress, strategy);
            if (valid) {
                String fileElement = getXMLElement(snapshot, strategy, folderPrefix);
                LOGGER.log(Level.FINE, "Writing {0}", fileElement);
                FileWriter.appendString(fileElement, xmlFileAddress);
                savedNum++;
            } else {
                LOGGER.log(Level.SEVERE, "Snapshot is invalid!");
                unvalidNum++;
            }
        }
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

    private String getXMLElement(ServiceDescriptionSnapshot snapshot, XMLStrategy strategy, String folderPrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("<file id=\"").append(snapshot.getId()).append("\" path=\"").append(folderPrefix).append(snapshot.getFileAddress().replace(".html", ".txt")).append("\">");
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
        Category category = getCategory(snapshot, strategy);
        if (category == null) {
            return "";
        } else {
            return category.getName();
        }
    }

    private Category getCategory(ServiceDescriptionSnapshot snapshot, XMLStrategy strategy) {
        switch (strategy) {
            case CONTEXT_CLASSIFIED:
                return snapshot.getPrimaryCategoryWithCtx();
            case PLAIN_CLASSIFIED:
                return snapshot.getPrimaryCategoryPlain();
        }
        return null;
    }

    private boolean validateSnapshot(ServiceDescriptionSnapshot snapshot, String withCtxReposAddress, String withoutCtxReposAddress, XMLStrategy strategy) throws IOException {
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

        if (withCtxFile.length() < 100) {
            LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} with context snapshot at {1} is too small", new Object[]{snapshot.getId(), withCtxFile.getPath()});
            return false;
        }

        if (!plainFile.exists() || plainFile.isDirectory()) {
            LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} does not have without context at {1}", new Object[]{snapshot.getId(), plainFile.getPath()});
            return false;
        }

        if (plainFile.length() < 100) {
            LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} without context snapshot at {1} is too small", new Object[]{snapshot.getId(), withCtxFile.getPath()});
            return false;
        }

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
        }

        return true;
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

        if (withCtxFile.length() < 100) {
            LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} with context snapshot at {1} is too small", new Object[]{snapshot.getId(), withCtxFile.getPath()});
            return false;
        }

        if (!plainFile.exists() || plainFile.isDirectory()) {
            LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} does not have without context at {1}", new Object[]{snapshot.getId(), plainFile.getPath()});
            return false;
        }

        if (plainFile.length() < 100) {
            LOGGER.log(Level.SEVERE, "Snapshot with ID : {0} without context snapshot at {1} is too small", new Object[]{snapshot.getId(), withCtxFile.getPath()});
            return false;
        }

        String ctxTempDirAddress = tempAddress.concat("/WithContext/");
        if (createSeparate) {
            ctxTempDirAddress = ctxTempDirAddress.concat(getCVE(snapshot, strategy) + "/");
        }

        String typeAddress = null;
        switch (snapshot.getType()) {
            case REST:
                typeAddress = "RESTS";
                break;
            case WSDL:
                typeAddress = "WSDLS";
                break;
            case WADL:
                typeAddress = "WADLS";
                break;
        }

        ctxTempDirAddress = ctxTempDirAddress.concat(typeAddress + "/");
        String tempCtxFileAddress = ctxTempDirAddress.concat(fileAddress);

        String plainTempDirAddress = tempAddress.concat("/WithoutContext/");
        if (createSeparate) {
            plainTempDirAddress = plainTempDirAddress.concat(getCVE(snapshot, strategy) + "/");
        }

        plainTempDirAddress = plainTempDirAddress.concat(typeAddress + "/");
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

            tempCtxFileAddress = ctxTempDirAddress.concat(fileAddress);
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

    private HashMap<Category, List<ServiceDescriptionSnapshot>> mapSnapshots(List<ServiceDescriptionSnapshot> snapshots, XMLStrategy strategy) {
        HashMap<Category, List<ServiceDescriptionSnapshot>> all = new HashMap<>();
        for (ServiceDescriptionSnapshot snapshot : snapshots) {
            Category category = getCategory(snapshot, strategy);
            if (!all.containsKey(category)) {
                all.put(category, new ArrayList<ServiceDescriptionSnapshot>());
            }

            all.get(category).add(snapshot);
        }
        return all;
    }

    private List<ServiceDescriptionSnapshot> chooseRandomSet(List<ServiceDescriptionSnapshot> original, int testingSize) {
        List<ServiceDescriptionSnapshot> testing = new ArrayList<>();

        for (int i = 0; i < testingSize; i++) {
            int random = ThreadLocalRandom.current().nextInt(0, original.size());
            testing.add(original.remove(random));
        }

        return testing;
    }

    private void distributeSnapshots(List<ServiceDescriptionSnapshot> snapshots, XMLStrategy strategy, double testingPercentage, HashMap<Category, List<ServiceDescriptionSnapshot>> trainingSet, HashMap<Category, List<ServiceDescriptionSnapshot>> testingSet) {
        HashMap<Category, List<ServiceDescriptionSnapshot>> all = mapSnapshots(snapshots, strategy);

        int testingSize = (int) ((snapshots.size() / all.keySet().size()) * testingPercentage);

        for (Map.Entry<Category, List<ServiceDescriptionSnapshot>> entrySet : all.entrySet()) {
            Category category = entrySet.getKey();
            List<ServiceDescriptionSnapshot> cluster = entrySet.getValue();
            testingSet.put(category, chooseRandomSet(cluster, testingSize));
            trainingSet.put(category, cluster);
        }
    }

    private HashMap<Category, List<List<ServiceDescriptionSnapshot>>> Create10FoldSets(List<ServiceDescriptionSnapshot> snapshots, XMLStrategy strategy) {
        HashMap<Category, List<List<ServiceDescriptionSnapshot>>> testingMap = new HashMap<>();
        HashMap<Category, List<ServiceDescriptionSnapshot>> all = mapSnapshots(snapshots, strategy);

        for (Map.Entry<Category, List<ServiceDescriptionSnapshot>> entrySet : all.entrySet()) {
            Category category = entrySet.getKey();
            List<ServiceDescriptionSnapshot> cluster = entrySet.getValue();
            List<List<ServiceDescriptionSnapshot>> testingSets = new ArrayList<>();
            int testingSize = cluster.size() / 10;
            for (int i = 0; i < 10; i++) {
                testingSets.add(chooseRandomSet(cluster, testingSize));
            }

            testingMap.put(category, testingSets);
        }

        return testingMap;
    }
}
