/*
 * Copyright 2014 Arash khodadadi.
 * <http://www.arashkhodadadi.com/>
 */
package cloudservices.brokerage.crawler.snapshotcreator;

import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.DAOException;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.v3.ServiceDescriptionDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.DAO.v3.ServiceDescriptionSnapshotDAO;
import cloudservices.brokerage.crawler.crawlingcommons.model.entities.v3.ServiceDescription;
import cloudservices.brokerage.crawler.crawlingcommons.model.entities.v3.ServiceDescriptionSnapshot;
import cloudservices.brokerage.crawler.crawlingcommons.model.enums.v3.ServiceDescriptionType;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hibernate.cfg.Configuration;

/**
 *
 * @author Arash Khodadadi <http://www.arashkhodadadi.com/>
 */
public class SnapshotImporter {

    private final static Logger LOGGER = Logger.getLogger(SnapshotCreator.class.getName());
    private int tempServicesNum;
    private int updatedServicesNum;
    private int tempSnapshotsNum;
    private int savedSnapshotsNum;

    public SnapshotImporter() {
    }

    public void Import(Configuration tempConfig, Configuration v3Configuration, ServiceDescriptionType type, long startingId, long endingId) throws Exception {
        try {
            LOGGER.log(Level.INFO, "Starting to Merge temp Repository...");
            LOGGER.log(Level.INFO, "Type : {0} and id between {1} and {2}", new Object[]{type, startingId, endingId});

            ServiceDescriptionDAO tempDescDAO = new ServiceDescriptionDAO();
            ServiceDescriptionSnapshotDAO tempSnapDAO = new ServiceDescriptionSnapshotDAO();
            ServiceDescriptionDAO.openSession(tempConfig);

            List<ServiceDescription> services = tempDescDAO.getSnapped(type, startingId, endingId);
            LOGGER.log(Level.INFO, "Total snaped services in temp : {0}", services.size());
            this.tempServicesNum = services.size();

            List<ServiceDescriptionSnapshot> snapshots = tempSnapDAO.getAll("ServiceDescriptionSnapshot");
            LOGGER.log(Level.INFO, "Total snapshots in temp : {0}", snapshots.size());
            this.tempSnapshotsNum = snapshots.size();

            ServiceDescriptionDAO sdDAO = new ServiceDescriptionDAO();
            ServiceDescriptionSnapshotDAO sdsDAO = new ServiceDescriptionSnapshotDAO();
            ServiceDescriptionDAO.openSession(v3Configuration);

            for (ServiceDescription service : services) {
                LOGGER.log(Level.INFO, "ID : {0}", service.getId());
                ServiceDescription indb = (ServiceDescription) sdDAO.load(ServiceDescription.class, service.getId());
                if (indb.getType() != service.getType()
                        || indb.getUrl().compareTo(service.getUrl()) != 0
                        || indb.getLastAvailableTime() != null
                        || indb.getLastUnavailableTime() != null) {
                    LOGGER.log(Level.SEVERE, "ID mismatch : {0}", service.getId());
                    throw new Exception("ID mismatch " + service.getId());
                }

                indb.setAvailable(service.available());
                indb.setLastAvailableTime(service.getLastAvailableTime());
                indb.setLastUnavailableTime(service.getLastUnavailableTime());
                sdDAO.saveOrUpdate(indb);
                this.updatedServicesNum++;
            }
            LOGGER.log(Level.INFO, "Merge from services successful");
            
            for (ServiceDescriptionSnapshot snapshot : snapshots) {
                LOGGER.log(Level.INFO, "Snapshot ID : {0}", snapshot.getId());
                ServiceDescriptionSnapshot indb = sdsDAO.getById(snapshot.getId());
                if(indb!=null){
                     LOGGER.log(Level.SEVERE, "Same ID already exists : {0}", snapshot.getId());
                    throw new Exception("Same ID already exists " + snapshot.getId());
                }
                
                ServiceDescriptionSnapshot newSnapshot = new ServiceDescriptionSnapshot(snapshot);
                ServiceDescription sd = (ServiceDescription) sdDAO.load(ServiceDescription.class, snapshot.getServiceDescription().getId());
                newSnapshot.setServiceDescription(sd);
                newSnapshot.setId(snapshot.getId());
                sdsDAO.save(snapshot);
                this.savedSnapshotsNum++;
            }

            LOGGER.log(Level.INFO, "Merge from temp Repository Successful");
        } catch (DAOException ex) {
            LOGGER.log(Level.SEVERE, "ERROR in reading temp database", ex);
        } finally {
            ServiceDescriptionDAO.closeSession();
        }
    }

    public int getTempServicesNum() {
        return tempServicesNum;
    }

    public void setTempServicesNum(int tempServicesNum) {
        this.tempServicesNum = tempServicesNum;
    }

    public int getUpdatedServicesNum() {
        return updatedServicesNum;
    }

    public void setUpdatedServicesNum(int updatedServicesNum) {
        this.updatedServicesNum = updatedServicesNum;
    }

    public int getTempSnapshotsNum() {
        return tempSnapshotsNum;
    }

    public void setTempSnapshotsNum(int tempSnapshotsNum) {
        this.tempSnapshotsNum = tempSnapshotsNum;
    }

    public int getSavedSnapshotsNum() {
        return savedSnapshotsNum;
    }

    public void setSavedSnapshotsNum(int savedSnapshotsNum) {
        this.savedSnapshotsNum = savedSnapshotsNum;
    }

    
}
