/*
 * Copyright 2014 Arash khodadadi.
 * <http://www.arashkhodadadi.com/>
 */
package cloudservices.brokerage.crawler.snapshotcreator;

import cloudservices.brokerage.commons.utils.file_utils.DirectoryUtil;
import cloudservices.brokerage.commons.utils.file_utils.FileWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;

/**
 *
 * @author Arash Khodadadi <http://www.arashkhodadadi.com/>
 */
public class HTMLNoiseRemover {

    private final static Logger LOGGER = Logger.getLogger(SnapshotCreator.class.getName());
    private int totalNum;
    private int savedNum;
    private int unvalidNum;

    public HTMLNoiseRemover() {

    }

    public void removeNoise(String reposAddress, boolean keepComments) {
        LOGGER.log(Level.INFO, "Starting to Remove Noise from all HTML files in: {0}", reposAddress);

        List<File> files = DirectoryUtil.getAllFiles(reposAddress, "html");
        LOGGER.log(Level.INFO, "{0} files found", files.size());
        totalNum = files.size();

        for (File file : files) {
            try {
                LOGGER.log(Level.INFO, "File : {0} found from address : {1}", new Object[]{file.getName(), file.getPath()});

                String text = Jsoup.parse(file, "UTF-8").text();
                LOGGER.log(Level.FINER, "Text : {0} found from html", text);

                if (keepComments) {
                    String context = getContextComments(file);
                    LOGGER.log(Level.FINER, "Context comments : {0} found from html", context);
                    text = context.concat(text);
                }

                String newFilePath = file.getPath().replace(".html", ".txt");
                File newFile = new File(newFilePath);
                if (newFile.exists()) {
                    LOGGER.log(Level.INFO, "File already exists in {0}", newFilePath);
                    newFile.delete();
                }
                LOGGER.log(Level.FINE, "Writing new file in {0}", newFilePath);
                FileWriter.appendString(text, newFilePath);
                savedNum++;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Cannot parse file " + file.getPath(), ex);
                unvalidNum++;
            }
        }

        LOGGER.log(Level.INFO, "Removing Noise Successfull");
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
                    context = context.concat(line);
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

}
