/*
 * Copyright 2014 Arash khodadadi.
 * <http://www.arashkhodadadi.com/>
 */
package cloudservices.brokerage.crawler.snapshotcreator;

/**
 *
 * @author Arash Khodadadi <http://www.arashkhodadadi.com/>
 */
public enum SnapshotStrategy {
    /**
     * All of services
     */
    ALL,
    /**
     * Context Updated
     */
    UPDATED, 
    /**
     * Never Snapped
     */
    NEVER_SNAPPED,
    /**
     * New Services
     */
    NEW,
    /**
     * Last Time was Unavailable
     */
    UNAVAILABLE
}
