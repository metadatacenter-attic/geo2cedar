package org.metadatacenter.ingestor.geo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.readers.geo.GEOReaderException;
import org.metadatacenter.readers.geo.formats.geometadb.GEOmetadbReader;
import org.metadatacenter.readers.geo.metadata.GEOSubmissionMetadata;
import org.metadatacenter.readers.geo.metadata.Sample;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class GeoPojo2BioSampleInstances {

    private static boolean tissueFound;
    private static boolean diseaseFound;

    public static void main (String[] args) throws GEOReaderException {
        if (args.length != 5)
            Usage();

        // Query GEOMetdatadb and create GEO metadata objects
        List<GEOSubmissionMetadata> geoSubmissionMetadataList = Collections.emptyList();
        String bioSampleTemplateFileName = null;

        String jsonBioSamplesDirectoryName = null;

        try {
            String geometadbFilename = args[0];
            int startIndex = Integer.parseInt(args[1]);
            int numberOfSeries = Integer.parseInt(args[2]);
            bioSampleTemplateFileName = args[3];
            jsonBioSamplesDirectoryName = args[4];

            GEOmetadbReader geometadbReader = new GEOmetadbReader(geometadbFilename);
            geoSubmissionMetadataList = geometadbReader.extractGEOSubmissionsMetadata(startIndex, numberOfSeries);

 /*
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

            */

        } catch (GEOReaderException e) {
            System.err.println(org.metadatacenter.ingestor.geo.GeoPojo2BioSampleInstances.class.getName() + ": Error reading: " + e.getMessage());
            System.exit(-1);
        } catch (NumberFormatException e) {
            System.err.println(org.metadatacenter.ingestor.geo.GeoPojo2BioSampleInstances.class.getName() + ": Error processing arguments: " + e.getMessage());
            System.exit(-1);
        }

        // Get the template id from the BioSample template file

        ObjectMapper mapper = new ObjectMapper();
        String bioSampleTemplateId = null;

        try {
            JsonNode node = mapper.readValue(new File(bioSampleTemplateFileName), JsonNode.class);
            bioSampleTemplateId = node.get("@id").asText();
            System.out.println("biosample templateId = " + bioSampleTemplateId);

        } catch (java.io.IOException e) {
            System.err.println(org.metadatacenter.ingestor.geo.GeoPojo2BioSampleInstances.class.getName() + ": Error parsing BioSample Template file: " + e.getMessage());
            System.exit(-1);
        }


        // Map GEO Metadatadb java objects to CEDAR GEO template instance objects
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        for (GEOSubmissionMetadata geoSubmissionMetadata : geoSubmissionMetadataList) {

            // Process GEO Samples

            for (String sampleName : geoSubmissionMetadata.getSamples().keySet()) {
                Sample sample = geoSubmissionMetadata.getSamples().get(sampleName);
                List<String> organisms = sample.getOrganisms();
                if (organisms.stream().filter(s -> s.equalsIgnoreCase("homo sapiens")).findFirst().isPresent()) {

                    org.metadatacenter.ingestor.biosample.BiosampleTemplate cedarBioSample = convertBioSampleMetadata(sample, bioSampleTemplateId);

                    if (tissueFound && diseaseFound) {
                        File bioSampleFile;

                        String jsonFilePath = jsonBioSamplesDirectoryName + String.format("%s.json", cedarBioSample.getSampleName().getValue());
                        bioSampleFile = new File(jsonFilePath);

                        try {
                            boolean bool;
                            bool = bioSampleFile.createNewFile();
                            if (bool) {
                                System.out.println(String.format("New file created: %s", bioSampleFile));
                            } else {
                                System.out.println(String.format("File overwritten: %s", bioSampleFile));
                            }

                            // Serialize the GEO instance
                            mapper.writeValue(bioSampleFile, cedarBioSample);

                        } catch (java.io.IOException e) {
                            System.err.println(org.metadatacenter.ingestor.geo.GeoPojo2BioSampleInstances.class.getName() + ": Error creating GEO Sample Instance file: " + e.getMessage());
                            System.exit(-1);
                        }
                    }
                }
            }
        }
    }


    private static org.metadatacenter.ingestor.biosample.BiosampleTemplate convertBioSampleMetadata(org.metadatacenter.readers.geo.metadata.Sample sample, String bioSampleTemplateId) {

        tissueFound = false;
        diseaseFound = false;

        org.metadatacenter.ingestor.biosample.BiosampleTemplate cedarBioSample = new org.metadatacenter.ingestor.biosample.BiosampleTemplate();

        cedarBioSample.setSchemaIsBasedOn(URI.create(bioSampleTemplateId));
        cedarBioSample.setSchemaName(sample.getGSM());
        cedarBioSample.setSchemaDescription(sample.getGSM() + " created by CEDAR's GEO Ingestor");

        org.metadatacenter.ingestor.biosample.SampleName sampleName = new org.metadatacenter.ingestor.biosample.SampleName();
        sampleName.setValue(sample.getGSM());
        cedarBioSample.setSampleName(sampleName);

        org.metadatacenter.ingestor.biosample.Isolate isolate = new org.metadatacenter.ingestor.biosample.Isolate();
        isolate.setValue(sample.getGSM());
        cedarBioSample.setIsolate(isolate);

        org.metadatacenter.ingestor.biosample.BiomaterialProvider bp = new org.metadatacenter.ingestor.biosample.BiomaterialProvider();
        bp.setValue("Biomaterial Provider");
        cedarBioSample.setBiomaterialProvider(bp);

        // only process characteristics of GEO Sample Channel #1
        org.metadatacenter.readers.geo.metadata.PerChannelSampleInfo r_chInfo = sample.getPerChannelInformation().get(1);

        org.metadatacenter.ingestor.biosample.Organism organism = new org.metadatacenter.ingestor.biosample.Organism();
        organism.setValue(r_chInfo.getOrganism());
        cedarBioSample.setOrganism(organism);

        List<org.metadatacenter.ingestor.biosample.OptionalAttribute> optAttrList = new ArrayList<>();
        org.metadatacenter.ingestor.biosample.OptionalAttribute optAttr;
        org.metadatacenter.ingestor.biosample.Name attrName;
        org.metadatacenter.ingestor.biosample.Value attrValue;

        for (String charsName : r_chInfo.getCharacteristics().keySet()) {
            String charValue = r_chInfo.getCharacteristics().get(charsName);
            // System.out.println("charsName = " + charsName + "; charsValue = " + charValue);

            // check for Tissue
            if (charsName.matches("(T|t)issue(s)?")) {
                org.metadatacenter.ingestor.biosample.Tissue tissue = new org.metadatacenter.ingestor.biosample.Tissue();
                tissue.setValue(charValue);
                cedarBioSample.setTissue(tissue);
                tissueFound = true;
            }

            // check for Age
            if (charsName.matches("\\b(A|a)ge")) {
                org.metadatacenter.ingestor.biosample.Age age = new org.metadatacenter.ingestor.biosample.Age();
                age.setValue(charValue);
                cedarBioSample.setAge(age);
            }

            // check for Sex
            if (charsName.matches("\\b(S|s)ex") || charsName.matches("\\b(G|g)ender")) {
                org.metadatacenter.ingestor.biosample.Sex sex = new org.metadatacenter.ingestor.biosample.Sex();
                sex.setValue(charValue);
                cedarBioSample.setSex(sex);
            }


            // check for Disease
            if (charsName.equalsIgnoreCase("Disease") || charsName.equalsIgnoreCase("Condition") || charsName.matches("(D|d)isease (S|s)tate")) {
                optAttr = new org.metadatacenter.ingestor.biosample.OptionalAttribute();

                attrName = new org.metadatacenter.ingestor.biosample.Name();
                attrName.setValue("disease");
                optAttr.setName(attrName);

                attrValue = new org.metadatacenter.ingestor.biosample.Value();
                attrValue.setValue(charValue);
                optAttr.setValue(attrValue);

                optAttrList.add(optAttr);
                diseaseFound = true;
            }

            // check for Disease Stage
            if (charsName.matches("(D|d)isease (S|s)tage")) {
                optAttr = new org.metadatacenter.ingestor.biosample.OptionalAttribute();

                attrName = new org.metadatacenter.ingestor.biosample.Name();
                attrName.setValue("disease stage");
                optAttr.setName(attrName);

                attrValue = new org.metadatacenter.ingestor.biosample.Value();
                attrValue.setValue(charValue);
                optAttr.setValue(attrValue);

                optAttrList.add(optAttr);
            }

            // check for Developmental Stage
            if (charsName.matches("(D|d)evelopmental (S|s)tage") || charsName.matches("(D|d)ev (S|s)tage") ) {
                optAttr = new org.metadatacenter.ingestor.biosample.OptionalAttribute();

                attrName = new org.metadatacenter.ingestor.biosample.Name();
                attrName.setValue("developmental stage");
                optAttr.setName(attrName);

                attrValue = new org.metadatacenter.ingestor.biosample.Value();
                attrValue.setValue(charValue);
                optAttr.setValue(attrValue);

                optAttrList.add(optAttr);
            }


            // check for Treatment
            if (charsName.matches("(T|t)reatment")) {
                optAttr = new org.metadatacenter.ingestor.biosample.OptionalAttribute();

                attrName = new org.metadatacenter.ingestor.biosample.Name();
                attrName.setValue("treatment");
                optAttr.setName(attrName);

                attrValue = new org.metadatacenter.ingestor.biosample.Value();
                attrValue.setValue(charValue);
                optAttr.setValue(attrValue);

                optAttrList.add(optAttr);
            }


            // check for Ethnicity
            if (charsName.matches("(E|e)thnicity")) {
                optAttr = new org.metadatacenter.ingestor.biosample.OptionalAttribute();

                attrName = new org.metadatacenter.ingestor.biosample.Name();
                attrName.setValue("ethnicity");
                optAttr.setName(attrName);

                attrValue = new org.metadatacenter.ingestor.biosample.Value();
                attrValue.setValue(charValue);
                optAttr.setValue(attrValue);

                optAttrList.add(optAttr);
            }

            // check for Race
            if (charsName.matches("(R|r)ace")) {
                optAttr = new org.metadatacenter.ingestor.biosample.OptionalAttribute();

                attrName = new org.metadatacenter.ingestor.biosample.Name();
                attrName.setValue("race");
                optAttr.setName(attrName);

                attrValue = new org.metadatacenter.ingestor.biosample.Value();
                attrValue.setValue(charValue);
                optAttr.setValue(attrValue);

                optAttrList.add(optAttr);
            }

        }

        // Add sample title
        optAttr = new org.metadatacenter.ingestor.biosample.OptionalAttribute();

        attrName = new org.metadatacenter.ingestor.biosample.Name();
        attrName.setValue("sample title");
        optAttr.setName(attrName);

        attrValue = new org.metadatacenter.ingestor.biosample.Value();
        attrValue.setValue(sample.getTitle());
        optAttr.setValue(attrValue);

        optAttrList.add(optAttr);

        // Add Description, if found
        if (sample.getDescription().isPresent()) {
            optAttr = new org.metadatacenter.ingestor.biosample.OptionalAttribute();

            attrName = new org.metadatacenter.ingestor.biosample.Name();
            attrName.setValue("description");
            optAttr.setName(attrName);

            attrValue = new org.metadatacenter.ingestor.biosample.Value();
            attrValue.setValue(sample.getDescription().get());
            optAttr.setValue(attrValue);

            optAttrList.add(optAttr);
        }

        cedarBioSample.setOptionalAttribute(optAttrList);

        return cedarBioSample;
    }

    private static void Usage() {
        System.err
                .println("Usage: " + org.metadatacenter.ingestor.geo.GeoPojo2BioSampleInstances.class.getName() + " <GEOmetadb Filename> <startIndex> <numberOfSeries>");
        System.exit(-1);
    }
}
