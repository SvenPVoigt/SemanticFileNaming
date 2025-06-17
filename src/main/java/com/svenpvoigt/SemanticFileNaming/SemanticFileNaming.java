/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.svenpvoigt.SemanticFileNaming;

import java.util.stream.Collectors;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.*;
import java.nio.file.DirectoryStream.Filter;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.*;
import org.apache.jena.reasoner.rulesys.*;
import org.apache.jena.query.*;
import java.util.*;
import java.util.function.Predicate;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFLanguages;
import java.util.regex.*;
import javax.xml.transform.stream.StreamResult;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.sparql.resultset.RDFOutput;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author sven
 */
public class SemanticFileNaming {
    static String specPrefix = "example:";
    
    public static void main(String[] args) throws Exception {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage;
        
        int n = args.length;
        
        NodeSetUtil.print.filterLevel = 0;
        BuiltinRegistry.theRegistry.register(new RelativeURINode());
        
        Path specPath = Path.of("data_test/spec_2.txt");
        Path dirPath = Path.of("data_test/dir_2.txt");
        Path modelPath;
        String specFile = "";
        
        
        if (n<2) {
            System.out.println("""
                Arguments Required.
                Arg 1: File path to a specification.
                Arg 2: File path to a list of file paths.
                Arg 3 [optional]: File path to a model.
                Arg 4 [optional]: Verbosity (integer).
            """);
            return;
        } else if (n==2) {
            specPath = Path.of(args[0]);
            dirPath = Path.of(args[1]);
            specFile = Files.readString(specPath);
        } else if (n==3) {
            specPath = Path.of(args[0]);
            dirPath = Path.of(args[1]);
            modelPath = Path.of(args[2]);
            specFile = Files.readString(specPath) + "\n" + Files.readString(modelPath);
        } else if (n==4) {
            specPath = Path.of(args[0]);
            dirPath = Path.of(args[1]);
            modelPath = Path.of(args[2]);
            specFile = Files.readString(specPath) + "\n" + Files.readString(modelPath);
            NodeSetUtil.print.filterLevel = Integer.parseInt(args[3]);
        }else {
            throw new Exception("Too many arguments. Need 0-2 arguments for spec and dir files to replace default");
        }
        
        specPrefix = extractPrefix(specFile);
        NodeSetUtil.specPrefix = specPrefix;
        String spec = extractSpec(specFile);
        String modelString = extractModel(specFile);
        Model model = ModelFactory.createDefaultModel();
        model.read( new ByteArrayInputStream(modelString.getBytes()), null, "TTL");
        
        
        HashMap<String, Model> implied = extractImplied(specFile).entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                k -> k.getKey(),
                                v -> ModelFactory.createDefaultModel()
                                        .read(new ByteArrayInputStream(v.getValue().getBytes()), null, "TTL"),
                                (v1, v2) -> v1,
                                HashMap::new
                        )
                );
        
        List rules = Rule.parseRules( extractRules(specFile) );
        
        String dir = Files.readString(dirPath);
        
        System.out.println("STATUS: Done parsing spec file");
        
        NodeSetUtil.dirname = dirPath.getFileName().toString();
        model = model.union(NodeSetUtil.fromDirectoryString(dir, spec, implied));
        
        System.out.println("STATUS: Done Process Filepaths");
        System.out.println("STATUS: Number of triples in model="+String.valueOf(model.size()));
        
        heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        System.out.println("Used memory: " + heapMemoryUsage.getUsed() + " bytes");
        System.out.println("Max memory: " + heapMemoryUsage.getMax() + " bytes");

        Reasoner reasoner = new GenericRuleReasoner(rules);
        
        model = model.union(ModelFactory.createInfModel(reasoner, model).getDeductionsModel());
        
        System.out.println("STATUS: Done Processing Rules");
        
        heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        System.out.println("Used memory: " + heapMemoryUsage.getUsed() + " bytes");
        System.out.println("Max memory: " + heapMemoryUsage.getMax() + " bytes");
        
        // Filter out nodeset properties
        
        Resource NodeSet = model.getResource("tag:SNF@null.net,2023:NodeSet");
        Resource din = model.getResource("tag:SNF@null.net,2023:in");

        List<Resource> subjList = model.listSubjectsWithProperty(RDF.type, NodeSet).toList();
        
        for (Resource subj: subjList) {
            model.removeAll(subj,null,null);
        }
        
        model = model
                .removeAll(NodeSet, null, null)
                .removeAll(null, din.as(Property.class), null)
                .removeAll(din, null, null)
                .removeAll(RDF.type, null, null);
        
        System.out.println("STATUS: Done removing nodesets");
        
        heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        System.out.println("Used memory: " + heapMemoryUsage.getUsed() + " bytes");
        System.out.println("Max memory: " + heapMemoryUsage.getMax() + " bytes");
        
        model.write(new FileOutputStream(new File(dirPath.getFileName().toString()+".ttl")), "TTL");
        
        System.out.println("STATUS: Done writing TTL to disk");
        
        Resource Data = model.getResource(specPrefix + "Data");
        
        System.out.println("STATUS: Number of files in model = "+
                String.valueOf(model.listResourcesWithProperty(RDF.type, Data).toList().size())
        );
        
    }
    
    
    static Model setAllNS(Model model) {
        model.setNsPrefix("d", "tag:SNF@null.net,2023:");
        model.setNsPrefix("", specPrefix);
        model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        
        return model;
    }
    
    
    static String extractPrefix(String specFile) {
        Pattern p = Pattern.compile("^PREFIX +(.+) *$", Pattern.MULTILINE);
        Matcher m = p.matcher(specFile);
        
        m.find();
        return m.group(1);
    }
    
    static String extractPart(String specFile, String startPattern, String endPattern) throws Exception {
        String out = "";
        
        boolean adding = false;
        
        boolean begin, end;
        
        for (String line: specFile.split("\n")) {
            begin = line.matches(startPattern);
            end = line.matches(endPattern);
            
            if (begin && !adding) adding = true;
            else if (begin && adding) throw new Exception("multiple "+startPattern+" lines found!");
            else if (!end && adding) out += "\n" + line;
            else if (end && !adding) throw new Exception(endPattern+" comes before a "+startPattern);
            else if (end) break;
        }
        
        out = out.replaceFirst("^\n+", "");
        out = out.replaceFirst("\n+$", "");
        out = out.replaceAll("\n+", "\n");
        
        return out;
    }
    
    static String extractSpec(String specFile) throws Exception {
        return extractPart(specFile, "^BEGIN Spec;", "^END Spec;");
    }
    
    static String extractModel(String specFile) throws Exception {
        return extractPart(specFile, "^BEGIN Model;", "^END Model;");
    }
    
    static String extractRules(String specFile) throws Exception {
        return extractPart(specFile, "^BEGIN Rules;", "^END Rules;");
    }
    
    static HashMap<String, String> extractImplied(String specFile) throws Exception {
        HashMap<String, String> out = new HashMap();
        
        Matcher m = Pattern.compile("^BEGIN (Implied\\s*=\\s*(.*)\\s*;)", Pattern.MULTILINE).matcher(specFile);
        
        while(m.find()) {
            String k = m.group(2);
            out.put(k, extractPart(specFile, m.group(0), "^END "+m.group(1)));
        }
        
        return out;
    }
    
    static String makeDir(Path root) throws Exception{
        return Files.find(root, 1000, (p, bfa) -> bfa.isRegularFile()).map(String::valueOf).collect(Collectors.joining("\n"));
    }
}
