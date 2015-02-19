/*
 * Copyright 2014 Arash khodadadi.
 * <http://www.arashkhodadadi.com/>
 */
package cloudservices.brokerage.crawler.snapshotcreator;

import cloudservices.brokerage.commons.utils.file_utils.DirectoryUtil;
import cloudservices.brokerage.commons.utils.file_utils.FileChecker;
import cloudservices.brokerage.commons.utils.file_utils.FileWriter;
import cloudservices.brokerage.commons.utils.url_utils.URLRequester;
import cloudservices.brokerage.commons.utils.validators.WSDLValidator;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.DAOException;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.v3.ServiceDescriptionDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.v3.ServiceDescriptionSnapshotDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.v3.ServiceProviderDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.entities.v3.ServiceDescription;
import cloudservices.brokerage.crawler.crawlingcommons.model.entities.v3.ServiceDescriptionSnapshot;
import cloudservices.brokerage.crawler.crawlingcommons.model.entities.v3.ServiceProvider;
import cloudservices.brokerage.crawler.crawlingcommons.model.enums.v3.ServiceDescriptionType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;

/**
 *
 * @author Arash Khodadadi <http://www.arashkhodadadi.com/>
 */
public class SnapshotCreator {

    private final static Logger LOGGER = Logger.getLogger(SnapshotCreator.class.getName());
    private final String[] userAgents;
    private final long politenessDelay;
    private final ServiceDescriptionDAO descriptionDAO;
    private final ServiceProviderDAO providerDAO;
    private final ServiceDescriptionSnapshotDAO snapshotDAO;
    private int totalDescNum;
    private int noResponse;
    private int notValidReponse;
    private int same;
    private int savedSnapshots;
    private int undefinedProviders;

    public SnapshotCreator(String[] userAgents, long politenessDelay) {
        this.userAgents = userAgents;
        this.politenessDelay = politenessDelay;
        this.descriptionDAO = new ServiceDescriptionDAO();
        this.providerDAO = new ServiceProviderDAO();
        this.snapshotDAO = new ServiceDescriptionSnapshotDAO();
    }

    public void CreateSnapshots(ServiceDescriptionType type, SnapshotStrategy strategy, String withCtxReposAddress, String withoutCtxReposAddress, long startingSnapshotId, long startingServiceId, long endingServiceId) throws DAOException, IOException, XMLStreamException {
        LOGGER.log(Level.INFO, "Snapping started for strategy : {0} and type : {1}", new Object[]{strategy, type});
        LOGGER.log(Level.INFO, "Snapping started from Id : {0} to Id : {1}", new Object[]{startingServiceId, endingServiceId});
        LOGGER.log(Level.INFO, "Starting saving snapshots from Id : {0}", startingSnapshotId);
        List<ServiceDescription> descriptions = this.getDescriptions(type, strategy, startingServiceId, endingServiceId);
        LOGGER.log(Level.INFO, "{0} Descriptions found for snapping", descriptions.size());
        totalDescNum = descriptions.size();
        Long snapshotId = startingSnapshotId;

        for (ServiceDescription description : descriptions) {
            LOGGER.log(Level.INFO, "Description Id : {0}", description.getId());
            try {
                ServiceDescriptionSnapshot last = null;
                if (strategy != SnapshotStrategy.NEVER_SNAPPED && strategy != SnapshotStrategy.NEW) {
                    last = snapshotDAO.getLast(description, type);
                }
                InputStream stream = getSnapshot(description.getUrl());
                // maybe other parts takes time
                Date now = new Date();

                if (stream != null) {
                    LOGGER.log(Level.FINER, "Received the response");
                    byte[] content = getContent(stream);

                    if (validate(content, type)) {
                        description.setAvailable(true);
                        description.setLastAvailableTime(now);
                        descriptionDAO.saveOrUpdate(description);

                        boolean flag = true;
                        if (last != null) {
                            LOGGER.log(Level.FINER, "There is already a snap with address : {0} and ID: {1}", new Object[]{last.getFileAddress(), last.getId()});
                            if (last.getFileAddress() != null) {
                                String plainFileAddress = withoutCtxReposAddress.concat(last.getFileAddress());
                                if (FileChecker.compare(new ByteArrayInputStream(content), new FileInputStream(plainFileAddress))) {
                                    last.setAccessedTime(now);
                                    snapshotDAO.saveOrUpdate(last);
                                    LOGGER.log(Level.INFO, "The previous snapshot has the same content");
                                    flag = false;
                                    same++;
                                } else {
                                    LOGGER.log(Level.INFO, "The previous snapshot has different content");
                                }
                            } else {
                                LOGGER.log(Level.SEVERE, "The Snapshot with ID has a null address : {0}", last.getId());
                            }
                        }
                        if (flag) { // indb = null or indb!=new

                            ServiceDescriptionSnapshot snapshot = new ServiceDescriptionSnapshot();
                            snapshot.setAccessedTime(now);
                            snapshot.setIsProcessed(false);
                            snapshot.setServiceDescription(description);
                            snapshot.setType(type);
                            if (startingSnapshotId == -1) {
                                snapshotId = (Long) snapshotDAO.save(snapshot);
                                LOGGER.log(Level.INFO, "New snapshot with ID : {0} saved", snapshotId);
                            }
                            snapshot.setId(snapshotId);

                            String providerName = "Undefined";
                            if (description.getServiceProvider() != null) {
                                ServiceProvider provider = (ServiceProvider) providerDAO.load(ServiceProvider.class, description.getServiceProvider().getId());
                                if (!provider.getName().isEmpty()) {
                                    providerName = provider.getName();
                                } else {
                                    LOGGER.log(Level.SEVERE, "Service Provider with ID : {0} does not have name", provider.getId());
                                    undefinedProviders++;
                                }
                            } else {
                                LOGGER.log(Level.SEVERE, "Service Description with ID : {0} does not have provider", description.getId());
                                undefinedProviders++;
                            }

                            LOGGER.log(Level.FINE, "Provider name is used : {0}", providerName);
                            String snapDirAddress = getSnapDirAddress(providerName);
                            LOGGER.log(Level.FINE, "Snap Directory address : {0}", snapDirAddress);
                            String ctxDir = withCtxReposAddress.concat(snapDirAddress);
                            String plainDir = withoutCtxReposAddress.concat(snapDirAddress);
                            DirectoryUtil.createDirs(ctxDir);
                            DirectoryUtil.createDirs(plainDir);
                            LOGGER.log(Level.FINER, "Directories created in {0} and in {1}", new Object[]{ctxDir, plainDir});

                            String fileName = getSnapFileName(type, snapshotId);
                            LOGGER.log(Level.FINE, "File name : {0}", fileName);

                            String ctxFileAddress = ctxDir.concat(fileName);
                            String plainFileAddress = plainDir.concat(fileName);

                            String ctx = getContext(description, providerName);
                            LOGGER.log(Level.FINER, "Contex : {0}", ctx);

                            InputStream temp = new ByteArrayInputStream(content);

                            FileWriter.writeInputStream(temp, new File(ctxFileAddress), ctx);
                            LOGGER.log(Level.INFO, "With context created successfully in {0}", ctxFileAddress);

                            InputStream temp2 = new ByteArrayInputStream(content);

                            FileWriter.writeInputStream(temp2, new File(plainFileAddress));
                            LOGGER.log(Level.INFO, "Without context created successfully in {0}", plainFileAddress);

                            snapshot.setFileAddress(snapDirAddress.concat(fileName));
                            if (startingSnapshotId == -1) {
                                snapshotDAO.saveOrUpdate(snapshot);
                            } else {
                                Long savedId = (Long) snapshotDAO.save(snapshot);
                                LOGGER.log(Level.INFO, "New snapshot with ID : {0} saved", savedId);
                                if ((long) savedId != (long) snapshotId) {
                                    LOGGER.log(Level.SEVERE, "SAVED ID : {0}  AND SNAPSHOT ID : {1} ARE DIFFERENT", new Object[]{savedId, snapshotId});
                                    System.exit(-1);
                                }
                            }
                            savedSnapshots++;
                            snapshotId++;
                        }
                    } else {
                        LOGGER.log(Level.INFO, "Response from URL : {0} is not valid", description.getUrl());
                        notValidReponse++;
                        description.setAvailable(false);
                        description.setLastUnavailableTime(now);
                        descriptionDAO.saveOrUpdate(description);
                    }
                } else {
                    LOGGER.log(Level.INFO, "Cannot receive from URL : {0}", description.getUrl());
                    noResponse++;
                    description.setAvailable(false);
                    description.setLastUnavailableTime(now);
                    descriptionDAO.saveOrUpdate(description);
                }
                this.delay();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "UNKNOWN ERROR!!! : " + ex.getMessage(), ex);
            }
        }
        LOGGER.log(Level.INFO, "Snapping ended for strategy : {0} and type : {1}", new Object[]{strategy, type});
    }

    private List<ServiceDescription> getDescriptions(ServiceDescriptionType type, SnapshotStrategy strategy, long startingServiceId, long endingServiceId) throws DAOException {
        switch (strategy) {
            case ALL:
                return descriptionDAO.getAllWithType(type, startingServiceId, endingServiceId);
            case NEW:
                return descriptionDAO.getBothTimesNull(type, startingServiceId, endingServiceId);
            case NEVER_SNAPPED:
                return descriptionDAO.getNeverAvailable(type, startingServiceId, endingServiceId);
            case UNAVAILABLE:
                return descriptionDAO.getUnavailable(type, startingServiceId, endingServiceId);
            case UPDATED:
                return descriptionDAO.getUpdated(type, startingServiceId, endingServiceId);
        }
        return new ArrayList<>();
    }

    private void delay() {
        try {
            long delay = 0;
            while (delay < this.politenessDelay) {
                long rand = Math.round(Math.random() * 10);
                delay = rand * this.politenessDelay;
            }
            LOGGER.log(Level.FINE, "Waiting for {0}", delay);
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private InputStream getSnapshot(String url) {
        InputStream stream = null;
        for (String userAgent : userAgents) {
            LOGGER.log(Level.FINE, "Sending GET request to URL : {0} with user agent : {1}", new Object[]{url, userAgent});
            try {
                return URLRequester.getInputStreamFromURL(url, userAgent);
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Cannot receive from URL : " + url + " with user agent " + userAgent, ex);
//                this.delay();
            }
        }

        return stream;
    }

    private byte[] getContent(InputStream stream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n = 0;
        while ((n = stream.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private boolean validate(byte[] content, ServiceDescriptionType type) throws XMLStreamException {
        switch (type) {
            case REST:
                return true;
            case WADL:
                return true; // SHOULD USE WADL VALIDATOR
            case WSDL:
                return WSDLValidator.validateWSDL(content);
        }
        return false;
    }

    private String getSnapDirAddress(String providerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Data");
        sb.append("/");
        sb.append(providerName);
        sb.append("/");
        return sb.toString();
    }

    private String getSnapFileName(ServiceDescriptionType type, Long snapshotId) {
        StringBuilder sb = new StringBuilder();
        sb.append(snapshotId);
        sb.append(".");
        switch (type) {
            case REST:
                sb.append("html");
            case WADL:
                sb.append("wadl");
            case WSDL:
                sb.append("wsdl");
        }
        return sb.toString();
    }

    private String getContext(ServiceDescription description, String providerCtx) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!--");
        sb.append("\n");
        sb.append(description.getTitle());
        sb.append("\n");
        sb.append(description.getDescription());
        sb.append("\n");
        sb.append(description.getTags());
        sb.append("\n");
        sb.append(providerCtx);
        sb.append("\n");
        sb.append("-->");
        sb.append("\n");
        return sb.toString();
    }

    public int getTotalDescNum() {
        return totalDescNum;
    }

    public int getNoResponse() {
        return noResponse;
    }

    public int getNotValidReponse() {
        return notValidReponse;
    }

    public int getSame() {
        return same;
    }

    public int getSavedSnapshots() {
        return savedSnapshots;
    }

    public int getUndefinedProviders() {
        return undefinedProviders;
    }
}
