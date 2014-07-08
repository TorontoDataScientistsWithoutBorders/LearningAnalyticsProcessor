/**
 * Copyright 2013 Unicon (R) Licensed under the
 * Educational Community License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.apereo.lap.services;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apereo.lap.services.csv.BaseCSVInputHandler;
import org.apereo.lap.services.csv.CSVInputHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.*;
import java.util.Map;

/**
 * Handles the inputs by reading the data into the temporary data storage
 * Validates the inputs and ensures the data is available to the pipeline processor
 * 
 * @author Aaron Zeckoski (azeckoski @ unicon.net) (azeckoski @ vt.edu)
 */
@Component
public class InputHandlerService {

    private static final Logger logger = LoggerFactory.getLogger(InputHandlerService.class);

    @Resource
    ConfigurationService configuration;

    @Resource
    StorageService storage;

    @PostConstruct
    public void init() {
        logger.info("INIT");
        if (configuration.config.getBoolean("input.copy.samples", false)) {
            copySampleExtractCSVs();
        }
        if (configuration.config.getBoolean("input.init.load.csv", false)) {
            loadCSVs();
        }
    }

    @PreDestroy
    public void destroy() {
        logger.info("DESTROY");
    }

    /**
     * Copies the 5 sample extract CSVs from the classpath to the inputs directory
     */
    public void copySampleExtractCSVs() {
        logger.info("copySampleExtractCSVs start");
        copySampleCSV("extracts/", "personal.csv");
        copySampleCSV("extracts/", "course.csv");
        copySampleCSV("extracts/", "enrollment.csv");
        copySampleCSV("extracts/", "grade.csv");
        copySampleCSV("extracts/", "activity.csv");
        logger.info("copySampleExtractCSVs to "+configuration.inputDirectory.getAbsolutePath()+" complete");
    }

    /**
     * Loads and verifies the 5 standard CSVs from the inputs directory
     */
    public void loadCSVs() {
        logger.info("load CSV files from: "+configuration.inputDirectory.getAbsolutePath());
        try {
            // Initialize the CSV handlers
            Map<String, CSVInputHandler> csvInputHandlers = BaseCSVInputHandler.makeCSVHandlers(configuration, storage.getTempJdbcTemplate());
            logger.info("Loaded "+csvInputHandlers.size()+" CSV InputHandlers: "+csvInputHandlers.keySet());

            // First we verify the CSV files
            for (CSVInputHandler csvInputHandler : csvInputHandlers.values()) {
                csvInputHandler.readCSV(true); // force it just in case
                logger.info(csvInputHandler.getCSVFilename()+" file and header appear valid");
            }
            // Next we load the data into the temp DB
            for (CSVInputHandler csvInputHandler : csvInputHandlers.values()) {
                InputHandler.ReadResult result = csvInputHandler.readInputIntoDB();
                if (!result.failures.isEmpty()) {
                    logger.error(result.failures.size()+" failures while parsing "+result.handledType+":\n"+ StringUtils.join(result.failures, "\n")+"\n");
                }
                logger.info(result.loaded+" lines from "+result.handledType+" (out of "+result.total+" lines) inserted into temp DB (with "+result.failed+" failures): "+result);

            }

            /*
            CSVReader personalCSV = loadCSV("personal.csv", 15, "ALTERNATIVE_ID");
            CSVReader courseCSV = loadCSV("course.csv", 4, "COURSE_ID");
            CSVReader enrollmentCSV = loadCSV("enrollment.csv", 4, "ALTERNATIVE_ID");
            CSVReader gradeCSV = loadCSV("grade.csv", 8, "ALTERNATIVE_ID");
            CSVReader activityCSV = loadCSV("activity.csv", 4, "ALTERNATIVE_ID");

            // load the content into the temp DB schema
            readCSVFileIntoDB("personal.csv", personalCSV, SQL_INSERT_PERSONAL, SQL_TYPES_PERSONAL);
            */

            logger.info("Loaded initial CSV files");
        } catch (Exception e) {
            String msg = "Failed to load CSVs file(s): "+e;
            logger.error(msg);
            throw new RuntimeException(msg, e);
        }
    }

    /**
     * Copies a file from a classpath dir to the file inputs dir
     * @param classpathDir the dir on the classpath with the same file (include trailing slash)
     * @param filename the csv file to copy from extracts to the inputs location
     * @throws java.lang.RuntimeException if the file cannot copy
     */
    private void copySampleCSV(String classpathDir, String filename) {
        try {
            IOUtils.copy(
                    InputHandlerService.class.getClassLoader().getResourceAsStream(classpathDir + filename),
                    new FileOutputStream(new File(configuration.inputDirectory, filename))
            );
        } catch (IOException e) {
            throw new RuntimeException("Cannot find the sample file to copy: "+filename);
        }
    }

    /**
     * @TODO delete this method
     * @deprecated REMOVE THIS
     */
    public void process() {
        logger.info("PROCESS");
        try {
            InputStream studentsCSV_IS = InputHandlerService.class.getClassLoader().getResourceAsStream("extracts/students.csv");
            InputStream coursesCSV_IS = InputHandlerService.class.getClassLoader().getResourceAsStream("extracts/courses.csv");
            InputStream gradesCSV_IS = InputHandlerService.class.getClassLoader().getResourceAsStream("extracts/grade.csv");
            InputStream usageCSV_IS = InputHandlerService.class.getClassLoader().getResourceAsStream("extracts/activity.csv");
            // now check the files by trying to read the header line from each one
            CSVReader studentsCSV = new CSVReader(new InputStreamReader(studentsCSV_IS));
            String[] check = studentsCSV.readNext();
            if (check != null && check.length >= 14 && "ALTERNATIVE_ID".equals(StringUtils.trimToEmpty(check[0]).toUpperCase())) {
                logger.info("Student CSV file and header appear valid");
            } else {
                throw new IllegalStateException("Students CSV file does not appear valid (no header or less than 14 required columns");
            }
            CSVReader coursesCSV = new CSVReader(new InputStreamReader(coursesCSV_IS));
            check = coursesCSV.readNext();
            if (check != null && check.length >= 4 && "COURSE_ID".equals(StringUtils.trimToEmpty(check[0]).toUpperCase())) {
                logger.info("Courses CSV file and header appear valid");
            } else {
                throw new IllegalStateException("Courses CSV file does not appear valid (no header or less than 4 required columns");
            }
            CSVReader gradesCSV = new CSVReader(new InputStreamReader(gradesCSV_IS));
            check = gradesCSV.readNext();
            if (check != null && check.length >= 8 && "ALTERNATIVE_ID".equals(StringUtils.trimToEmpty(check[0]).toUpperCase())) {
                logger.info("Grades CSV file and header appear valid");
            } else {
                throw new IllegalStateException("Grades CSV file does not appear valid (no header or less than 4 required columns");
            }
            CSVReader usageCSV = new CSVReader(new InputStreamReader(usageCSV_IS));
            check = usageCSV.readNext();
            if (check != null && check.length >= 4 && "ALTERNATIVE_ID".equals(StringUtils.trimToEmpty(check[0]).toUpperCase())) {
                logger.info("Usage CSV file and header appear valid");
            } else {
                throw new IllegalStateException("Usage CSV file does not appear valid (no header or less than 4 required columns");
            }
            logger.info("Loaded initial CSV files: ");
        } catch (Exception e) {
            String msg = "Failed to load CSVs file(s) and init the kettle pre-processor: "+e;
            logger.error(msg);
            throw new RuntimeException(msg, e);
        }
    }

}