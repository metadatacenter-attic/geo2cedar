package org.metadatacenter.ingestor.geo;


import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.ingestor.geo_template.GEOSample;
import org.metadatacenter.readers.geo.GEOReaderException;
import org.metadatacenter.readers.geo.formats.geometadb.GEOmetadbReader;
import org.metadatacenter.readers.geo.formats.geometadb.GEOmetadbRead;
import org.metadatacenter.readers.geo.metadata.GEOSubmissionMetadata;
import org.metadatacenter.readers.geo.metadata.Sample;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

public class GeoPojo2CedarInstances
{

    public static void main (String[] args) throws GEOReaderException {
        if (args.length != 5)
            Usage();

        // Query GEOMetdatadb and create GEO metadata objects
        List<GEOSubmissionMetadata> geoSubmissionMetadataList = Collections.emptyList();
        String jsonDirectoryName = null;
        String geoTemplateFileName = null;

        try {
            String geometadbFilename = args[0];
            int startIndex = Integer.parseInt(args[1]);
            int numberOfSeries = Integer.parseInt(args[2]);
            geoTemplateFileName = args[3];
            jsonDirectoryName = args[4];

            GEOmetadbReader geometadbReader = new GEOmetadbReader(geometadbFilename);
            geoSubmissionMetadataList = geometadbReader.extractGEOSubmissionsMetadata(startIndex, numberOfSeries);

            for (GEOSubmissionMetadata geoSubmissionMetadata : geoSubmissionMetadataList) {
                for (String sampleName : geoSubmissionMetadata.getSamples().keySet()) {
                    StringBuilder sb = new StringBuilder();
                    Sample sample = geoSubmissionMetadata.getSamples().get(sampleName);
                    sb.append("\nsampleName=" + sampleName + " \n");
                    for (String orgsName : sample.getOrganisms()) {
                        sb.append("\n Organism = " + orgsName + " \n");
                    }
                    for (String molsName : sample.getMolecules()) {
                        sb.append("\n Molecule = " + molsName + " \n");
                    }
                    for (String charsName : sample.getCharacteristics().keySet()) {
                        String value = sample.getCharacteristics().get(charsName);
                        sb.append("\n " + charsName + " = " + value + " \n");
                    }
                    System.out.println("geo Sample Metadata: " + sb.toString());
                }
            }

        } catch (GEOReaderException e) {
            System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error reading: " + e.getMessage());
            System.exit(-1);
        } catch (NumberFormatException e) {
            System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error processing arguments: " + e.getMessage());
            System.exit(-1);
        }

        // Get the template id from the GEO template file

        ObjectMapper mapper = new ObjectMapper();
        String templateId = null;

        try {
            JsonNode node = mapper.readValue(new File(geoTemplateFileName), JsonNode.class);
            templateId = node.get("@id").asText();
            System.out.println("templateId = " + templateId);

        } catch (java.io.IOException e) {
            System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error parsing GEO Template file: " + e.getMessage());
            System.exit(-1);
        }


        // Map GEO Metadatadb java objects to CEDAR GEO template instance objects
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        for (GEOSubmissionMetadata geoSubmissionMetadata : geoSubmissionMetadataList) {
            org.metadatacenter.ingestor.geo_template.GeoTemplate cedarGeo = convertGeoMetadata(geoSubmissionMetadata, templateId);

            File geoFile;

            String jsonFilePath = jsonDirectoryName + String.format("%s.json", cedarGeo.getGEOSeries().getSeriesID().getValue());
            geoFile = new File(jsonFilePath);

            try {
                boolean bool;
                bool = geoFile.createNewFile();
                if (bool) {
                    System.out.println(String.format("New file created: %s", geoFile));
                } else {
                    System.out.println(String.format("File overwritten: %s", geoFile));
                }

                // Serialize the GEO instance
                mapper.writeValue(geoFile, cedarGeo);

            } catch (java.io.IOException e) {
                System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error creating GEO Instance file: " + e.getMessage());
                System.exit(-1);
            }


        }

    }

    private static org.metadatacenter.ingestor.geo_template.GeoTemplate convertGeoMetadata(GEOSubmissionMetadata geoSubmissionMetadata, String templateId) {
        // Process each DataElement
        //System.out.println("Processing DataElement....");

        // Create a DataElement Java object (which we will serialize as a CEDAR template instance) for each DataElement
        org.metadatacenter.ingestor.geo_template.GeoTemplate cedarGeo = new org.metadatacenter.ingestor.geo_template.GeoTemplate();

        cedarGeo.setSchemaIsBasedOn(URI.create(templateId));
        cedarGeo.setSchemaName(geoSubmissionMetadata.getGSE());
        cedarGeo.setSchemaDescription(geoSubmissionMetadata.getGSE() + " created by CEDAR's GEO Ingestor");

        cedarGeo.setGEOSeries(convertGeoSeries(geoSubmissionMetadata));

        return cedarGeo;
    }

    private static org.metadatacenter.ingestor.geo_template.GEOSeries convertGeoSeries(GEOSubmissionMetadata geoSubmissionMetadata) {


        // Create a GEO Series Java object (which we will serialize as a CEDAR template instance)
        org.metadatacenter.ingestor.geo_template.GEOSeries cedarGeoSeries = new org.metadatacenter.ingestor.geo_template.GEOSeries();

        org.metadatacenter.ingestor.geo_template.SeriesID seriesID = new org.metadatacenter.ingestor.geo_template.SeriesID();
        seriesID.setValue(geoSubmissionMetadata.getSeries().getGSE());
        cedarGeoSeries.setSeriesID(seriesID);

        org.metadatacenter.ingestor.geo_template.Title title = new org.metadatacenter.ingestor.geo_template.Title();
        title.setValue(geoSubmissionMetadata.getSeries().getTitle());
        cedarGeoSeries.setTitle(title);

        // get the Samples
        cedarGeoSeries.setGEOSample(convertGeoSample(geoSubmissionMetadata));

        return cedarGeoSeries;
    }

    private static List<org.metadatacenter.ingestor.geo_template.GEOSample> convertGeoSample(GEOSubmissionMetadata geoSubmissionMetadata) {

        List<org.metadatacenter.ingestor.geo_template.GEOSample> geoSampleList = new ArrayList<>();

        // Create a DataElement Java object (which we will serialize as a CEDAR template instance) for each DataElement
            for (String sampleName : geoSubmissionMetadata.getSamples().keySet()) {
                Sample sample = geoSubmissionMetadata.getSamples().get(sampleName);
                org.metadatacenter.ingestor.geo_template.GEOSample cedarGeoSample= new org.metadatacenter.ingestor.geo_template.GEOSample();

                org.metadatacenter.ingestor.geo_template.SampleID sampleID = new org.metadatacenter.ingestor.geo_template.SampleID();
                sampleID.setValue(sampleName);
                cedarGeoSample.setSampleID(sampleID);

                List<org.metadatacenter.ingestor.geo_template.GEOSampleCharacteristicsOther> geoCharsList = new ArrayList<>();

                for (String charsName : sample.getCharacteristics().keySet()) {
                    String charValue = sample.getCharacteristics().get(charsName);
                    // System.out.println("charsName = " + charsName + "; charsValue = " + charValue);
                    org.metadatacenter.ingestor.geo_template.GEOSampleCharacteristicsOther sampleCharOther = new org.metadatacenter.ingestor.geo_template.GEOSampleCharacteristicsOther();
                    org.metadatacenter.ingestor.geo_template.Tag tag = new org.metadatacenter.ingestor.geo_template.Tag();
                    tag.setValue(charsName);
                    org.metadatacenter.ingestor.geo_template.Value_ value = new org.metadatacenter.ingestor.geo_template.Value_();
                    value.setValue(charValue);
                    sampleCharOther.setTag(tag);
                    sampleCharOther.setValue(value);
                    geoCharsList.add(sampleCharOther);
                }

                cedarGeoSample.setGEOSampleCharacteristicsOther(geoCharsList);



/*
                sb.append("\nsampleName=" + sampleName + " \n");
                for (String orgsName : sample.getOrganisms()) {
                    sb.append("\n Organism = " + orgsName + " \n");
                }
                for (String molsName : sample.getMolecules()) {
                    sb.append("\n Molecule = " + molsName + " \n");
                }
                for (String charsName : sample.getCharacteristics().keySet()) {
                    String value = sample.getCharacteristics().get(charsName);
                    sb.append("\n " + charsName + " = " + value + " \n");
                }
                System.out.println("geo Sample Metadata: " + sb.toString());
*/

                geoSampleList.add(cedarGeoSample);

            }

        return geoSampleList;
    }

    private static void Usage()
    {
        System.err
                .println("Usage: " + GeoPojo2CedarInstances.class.getName() + " <GEOmetadb Filename> <startIndex> <numberOfSeries>");
        System.exit(-1);
    }
}
