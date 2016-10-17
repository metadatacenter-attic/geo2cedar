package org.metadatacenter.ingestor.geo;


import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.ingestor.geo_series.GeoSeriesTemplate;
import org.metadatacenter.readers.geo.GEOReaderException;
import org.metadatacenter.readers.geo.formats.geometadb.GEOmetadbReader;
import org.metadatacenter.readers.geo.formats.geometadb.GEOmetadbRead;
import org.metadatacenter.readers.geo.metadata.GEOSubmissionMetadata;
import org.metadatacenter.readers.geo.metadata.PerChannelSampleInfo;
import org.metadatacenter.readers.geo.metadata.Sample;
import org.metadatacenter.readers.geo.metadata.Platform;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

public class GeoPojo2CedarInstances
{

    public static void main (String[] args) throws GEOReaderException {
        if (args.length != 9)
            Usage();

        // Query GEOMetdatadb and create GEO metadata objects
        List<GEOSubmissionMetadata> geoSubmissionMetadataList = Collections.emptyList();
        String geoSeriesTemplateFileName = null;
        String geoSampleTemplateFileName = null;
        String geoPlatformTemplateFileName = null;

        String jsonSeriesDirectoryName = null;
        String jsonSamplesDirectoryName = null;
        String jsonPlatformsDirectoryName = null;

        try {
            String geometadbFilename = args[0];
            int startIndex = Integer.parseInt(args[1]);
            int numberOfSeries = Integer.parseInt(args[2]);
            geoSeriesTemplateFileName = args[3];
            geoSampleTemplateFileName = args[4];
            geoPlatformTemplateFileName = args[5];
            jsonSeriesDirectoryName = args[6];
            jsonSamplesDirectoryName = args[7];
            jsonPlatformsDirectoryName = args[8];

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

        // Get the template id from the GEO Series template file

        ObjectMapper mapper = new ObjectMapper();
        String seriesTemplateId = null;

        try {
            JsonNode node = mapper.readValue(new File(geoSeriesTemplateFileName), JsonNode.class);
            seriesTemplateId = node.get("@id").asText();
            System.out.println("series templateId = " + seriesTemplateId);

        } catch (java.io.IOException e) {
            System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error parsing GEO Series Template file: " + e.getMessage());
            System.exit(-1);
        }

        // Get the template id from the GEO Sample template file

        mapper = new ObjectMapper();
        String sampleTemplateId = null;

        try {
            JsonNode node = mapper.readValue(new File(geoSampleTemplateFileName), JsonNode.class);
            sampleTemplateId = node.get("@id").asText();
            System.out.println("sample templateId = " + sampleTemplateId);

        } catch (java.io.IOException e) {
            System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error parsing GEO Sample Template file: " + e.getMessage());
            System.exit(-1);
        }

        // Get the template id from the GEO Platform template file

        mapper = new ObjectMapper();
        String platformTemplateId = null;

        try {
            JsonNode node = mapper.readValue(new File(geoPlatformTemplateFileName), JsonNode.class);
            platformTemplateId = node.get("@id").asText();
            System.out.println("platform templateId = " + platformTemplateId);

        } catch (java.io.IOException e) {
            System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error parsing GEO Platform Template file: " + e.getMessage());
            System.exit(-1);
        }

        // Map GEO Metadatadb java objects to CEDAR GEO template instance objects
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        for (GEOSubmissionMetadata geoSubmissionMetadata : geoSubmissionMetadataList) {

            // Process GEO Series
            org.metadatacenter.ingestor.geo_series.GeoSeriesTemplate cedarGeoSeries = convertGeoSeriesMetadata(geoSubmissionMetadata, seriesTemplateId);

            File geoSeriesFile;

            String jsonFilePath = jsonSeriesDirectoryName + String.format("%s.json", cedarGeoSeries.getSeriesID().getValue());
            geoSeriesFile = new File(jsonFilePath);

            try {
                boolean bool;
                bool = geoSeriesFile.createNewFile();
                if (bool) {
                    System.out.println(String.format("New file created: %s", geoSeriesFile));
                } else {
                    System.out.println(String.format("File overwritten: %s", geoSeriesFile));
                }

                // Serialize the GEO instance
                mapper.writeValue(geoSeriesFile, cedarGeoSeries);

            } catch (java.io.IOException e) {
                System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error creating GEO Series Instance file: " + e.getMessage());
                System.exit(-1);
            }

            // Process GEO Samples

            for (String sampleName : geoSubmissionMetadata.getSamples().keySet()) {
                Sample sample = geoSubmissionMetadata.getSamples().get(sampleName);

                org.metadatacenter.ingestor.geo_sample.GeoSampleTemplate cedarGeoSample = convertGeoSampleMetadata(sample, sampleTemplateId);

                File geoSampleFile;

                jsonFilePath = jsonSamplesDirectoryName + String.format("%s.json", cedarGeoSample.getSampleID().getValue());
                geoSampleFile = new File(jsonFilePath);

                try {
                    boolean bool;
                    bool = geoSampleFile.createNewFile();
                    if (bool) {
                        System.out.println(String.format("New file created: %s", geoSampleFile));
                    } else {
                        System.out.println(String.format("File overwritten: %s", geoSampleFile));
                    }

                    // Serialize the GEO instance
                    mapper.writeValue(geoSampleFile, cedarGeoSample);

                } catch (java.io.IOException e) {
                    System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error creating GEO Sample Instance file: " + e.getMessage());
                    System.exit(-1);
                }
            }

            // Process GEO Platforms

            for (org.metadatacenter.readers.geo.metadata.Platform platform : geoSubmissionMetadata.getPlatforms()) {

                org.metadatacenter.ingestor.geo_platform.GeoPlatformTemplate cedarPlatform = convertGeoPlatformMetadata(platform, platformTemplateId);

                File geoPlatformFile;

                jsonFilePath = jsonPlatformsDirectoryName + String.format("%s.json", cedarPlatform.getPlatformID().getValue());
                geoPlatformFile = new File(jsonFilePath);

                try {
                    boolean bool;
                    bool = geoPlatformFile.createNewFile();
                    if (bool) {
                        System.out.println(String.format("New file created: %s", geoPlatformFile));
                    } else {
                        System.out.println(String.format("File overwritten: %s", geoPlatformFile));
                    }

                    // Serialize the GEO instance
                    mapper.writeValue(geoPlatformFile, cedarPlatform);

                } catch (java.io.IOException e) {
                    System.err.println(GeoPojo2CedarInstances.class.getName() + ": Error creating GEO Platform Instance file: " + e.getMessage());
                    System.exit(-1);
                }
            }
        }
    }



    private static org.metadatacenter.ingestor.geo_series.GeoSeriesTemplate convertGeoSeriesMetadata(GEOSubmissionMetadata geoSubmissionMetadata, String seriesTemplateId) {
        // Process each DataElement
        //System.out.println("Processing DataElement....");

        // Create a DataElement Java object (which we will serialize as a CEDAR template instance) for each DataElement
        org.metadatacenter.ingestor.geo_series.GeoSeriesTemplate cedarGeoSeries = new org.metadatacenter.ingestor.geo_series.GeoSeriesTemplate();

        cedarGeoSeries.setSchemaIsBasedOn(URI.create(seriesTemplateId));
        cedarGeoSeries.setSchemaName(geoSubmissionMetadata.getGSE());
        cedarGeoSeries.setSchemaDescription(geoSubmissionMetadata.getGSE() + " created by CEDAR's GEO Ingestor");


        org.metadatacenter.ingestor.geo_series.SeriesID seriesID = new org.metadatacenter.ingestor.geo_series.SeriesID();
        seriesID.setValue(geoSubmissionMetadata.getSeries().getGSE());
        cedarGeoSeries.setSeriesID(seriesID);

        org.metadatacenter.ingestor.geo_series.SeriesTitle title = new org.metadatacenter.ingestor.geo_series.SeriesTitle();
        title.setValue(geoSubmissionMetadata.getSeries().getTitle());
        cedarGeoSeries.setSeriesTitle(title);

        org.metadatacenter.ingestor.geo_series.SeriesSummary summary = new org.metadatacenter.ingestor.geo_series.SeriesSummary();
        if (!geoSubmissionMetadata.getSeries().getSummary().isEmpty())
            summary.setValue(geoSubmissionMetadata.getSeries().getSummary().get(0));
        cedarGeoSeries.setSeriesSummary(summary);

        org.metadatacenter.ingestor.geo_series.SeriesOverallDesign design = new org.metadatacenter.ingestor.geo_series.SeriesOverallDesign();
        if (!geoSubmissionMetadata.getSeries().getOverallDesign().isEmpty())
            design.setValue(geoSubmissionMetadata.getSeries().getOverallDesign().get(0));
        cedarGeoSeries.setSeriesOverallDesign(design);

        List<org.metadatacenter.ingestor.geo_series.PubMedID> pubmedIds = new ArrayList<>();
        for (String pid : geoSubmissionMetadata.getSeries().getPubMedIDs()) {
            org.metadatacenter.ingestor.geo_series.PubMedID pubMedID = new org.metadatacenter.ingestor.geo_series.PubMedID();
            pubMedID.setValue(pid);
            pubmedIds.add(pubMedID);
        }
        cedarGeoSeries.setPubMedID(pubmedIds);

        List<org.metadatacenter.ingestor.geo_series.SampleID> sampleIds = new ArrayList<>();
        for (String sampleName : geoSubmissionMetadata.getSamples().keySet()) {
            Sample sample = geoSubmissionMetadata.getSamples().get(sampleName);

            org.metadatacenter.ingestor.geo_series.SampleID sampleID = new org.metadatacenter.ingestor.geo_series.SampleID();
            sampleID.setValue(sample.getGSM());
            sampleIds.add(sampleID);
        }
        cedarGeoSeries.setSampleIDs(sampleIds);

/*        List<org.metadatacenter.ingestor.geo_series.Contributor> contributors = new ArrayList<>();
        for (org.metadatacenter.readers.geo.metadata.Contributor contributor : geoSubmissionMetadata.getSeries().getContributors()) {
            contributors.add()
            System.out.println(temp);
        }*/

        return cedarGeoSeries;
    }


    private static org.metadatacenter.ingestor.geo_sample.GeoSampleTemplate convertGeoSampleMetadata(org.metadatacenter.readers.geo.metadata.Sample sample, String sampleTemplateId) {

        org.metadatacenter.ingestor.geo_sample.GeoSampleTemplate cedarGeoSample = new org.metadatacenter.ingestor.geo_sample.GeoSampleTemplate();

        cedarGeoSample.setSchemaIsBasedOn(URI.create(sampleTemplateId));
        cedarGeoSample.setSchemaName(sample.getGSM());
        cedarGeoSample.setSchemaDescription(sample.getGSM() + " created by CEDAR's GEO Ingestor");

        org.metadatacenter.ingestor.geo_sample.SampleID sampleID = new org.metadatacenter.ingestor.geo_sample.SampleID();
        sampleID.setValue(sample.getGSM());
        cedarGeoSample.setSampleID(sampleID);

        org.metadatacenter.ingestor.geo_sample.Title title = new org.metadatacenter.ingestor.geo_sample.Title();
        title.setValue(sample.getTitle());
        cedarGeoSample.setTitle(title);

        if (sample.getDescription().isPresent()) {
            org.metadatacenter.ingestor.geo_sample.Description desc = new org.metadatacenter.ingestor.geo_sample.Description();
            desc.setValue(sample.getDescription().get());
            cedarGeoSample.setDescription(desc);
        }

        org.metadatacenter.ingestor.geo_sample.PlatformID pid = new org.metadatacenter.ingestor.geo_sample.PlatformID();
        pid.setValue(sample.getGPL());
        cedarGeoSample.setPlatformID(pid);

        List<org.metadatacenter.ingestor.geo_sample.PerChannelSampleInfo> chList = new ArrayList<>();
        for (Integer chNum : sample.getPerChannelInformation().keySet()) {
            org.metadatacenter.readers.geo.metadata.PerChannelSampleInfo r_chInfo = sample.getPerChannelInformation().get(chNum);

            org.metadatacenter.ingestor.geo_sample.PerChannelSampleInfo chInfo = new org.metadatacenter.ingestor.geo_sample.PerChannelSampleInfo();

            org.metadatacenter.ingestor.geo_sample.ChannelNumber chn = new org.metadatacenter.ingestor.geo_sample.ChannelNumber();
            chn.setValue(chNum.toString());
            chInfo.setChannelNumber(chn);

            org.metadatacenter.ingestor.geo_sample.SourceName sn = new org.metadatacenter.ingestor.geo_sample.SourceName();
            sn.setValue(r_chInfo.getSourceName());
            chInfo.setSourceName(sn);

            org.metadatacenter.ingestor.geo_sample.Organism organism = new org.metadatacenter.ingestor.geo_sample.Organism();
            organism.setValue(r_chInfo.getOrganism());
            chInfo.setOrganism(organism);

            org.metadatacenter.ingestor.geo_sample.Molecule molecule = new org.metadatacenter.ingestor.geo_sample.Molecule();
            molecule.setValue(r_chInfo.getMolecule());
            chInfo.setMolecule(molecule);

            org.metadatacenter.ingestor.geo_sample.Label label = new org.metadatacenter.ingestor.geo_sample.Label();
            label.setValue(r_chInfo.getLabel());
            chInfo.setLabel(label);

            List<org.metadatacenter.ingestor.geo_sample.Characteristic> charsList = new ArrayList<>();

            for (String charsName : r_chInfo.getCharacteristics().keySet()) {
                String charValue = r_chInfo.getCharacteristics().get(charsName);
                // System.out.println("charsName = " + charsName + "; charsValue = " + charValue);
                org.metadatacenter.ingestor.geo_sample.Characteristic sampleChar = new org.metadatacenter.ingestor.geo_sample.Characteristic();
                org.metadatacenter.ingestor.geo_sample.Tag tag = new org.metadatacenter.ingestor.geo_sample.Tag();
                tag.setValue(charsName);
                org.metadatacenter.ingestor.geo_sample.Value value = new org.metadatacenter.ingestor.geo_sample.Value();
                value.setValue(charValue);
                sampleChar.setTag(tag);
                sampleChar.setValue(value);
                charsList.add(sampleChar);
            }
            chInfo.setCharacteristics(charsList);

            chList.add(chInfo);
        }

        cedarGeoSample.setPerChannelSampleInfo(chList);

        return cedarGeoSample;
    }

    private static org.metadatacenter.ingestor.geo_platform.GeoPlatformTemplate convertGeoPlatformMetadata(org.metadatacenter.readers.geo.metadata.Platform platform, String platformTemplateId) {

        org.metadatacenter.ingestor.geo_platform.GeoPlatformTemplate cedarGeoPlatform = new org.metadatacenter.ingestor.geo_platform.GeoPlatformTemplate();

        cedarGeoPlatform.setSchemaIsBasedOn(URI.create(platformTemplateId));
        cedarGeoPlatform.setSchemaName(platform.getGPL());
        cedarGeoPlatform.setSchemaDescription(platform.getGPL() + " created by CEDAR's GEO Ingestor");

        org.metadatacenter.ingestor.geo_platform.PlatformID platformID = new org.metadatacenter.ingestor.geo_platform.PlatformID();
        platformID.setValue(platform.getGPL());
        cedarGeoPlatform.setPlatformID(platformID);

        org.metadatacenter.ingestor.geo_platform.Title title = new org.metadatacenter.ingestor.geo_platform.Title();
        title.setValue(platform.getTitle());
        cedarGeoPlatform.setTitle(title);

        org.metadatacenter.ingestor.geo_platform.Organism organism = new org.metadatacenter.ingestor.geo_platform.Organism();
        organism.setValue(platform.getOrganism());
        cedarGeoPlatform.setOrganism(organism);

        org.metadatacenter.ingestor.geo_platform.Distribution distribution = new org.metadatacenter.ingestor.geo_platform.Distribution();
        distribution.setValue(platform.getDistribution());
        cedarGeoPlatform.setDistribution(distribution);

        if (platform.getManufacturer().isPresent()) {
            org.metadatacenter.ingestor.geo_platform.Manufacturer manufacturer = new org.metadatacenter.ingestor.geo_platform.Manufacturer();
            manufacturer.setValue(platform.getManufacturer().get());
            cedarGeoPlatform.setManufacturer(manufacturer);
        }

        return cedarGeoPlatform;
    }

    private static void Usage()
    {
        System.err
                .println("Usage: " + GeoPojo2CedarInstances.class.getName() + " <GEOmetadb Filename> <startIndex> <numberOfSeries>");
        System.exit(-1);
    }
}
