/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.svenpvoigt.SemanticFileNaming;
import java.util.*;
import java.util.regex.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;
//import org.apache.jena.*;
import org.apache.jena.riot.*;
import com.svenpvoigt.SemanticFileNaming.EasyLogger;
import java.net.URI;
import java.net.URLEncoder;

/**
 *
 * @author sven
 */
class ProcessingException extends Exception {
    public ProcessingException(String reason) {
        super("Error in Processing Path. Reason: " + reason);
    }
}
class SpecificationException extends Exception {
    String message;
    
    public SpecificationException(char c, String path, int state, List<SpecPart> Q) {
        super();
        this.message = "Error in SpecFile with char: " + c + ", after: " + path;
        this.message += ", with state: " + String.valueOf(state);
        this.message += "\n" + Q.toString();
    }
    
    @Override
    public String getMessage() {
        if (this.message!=null) return this.message;
        
        return super.getMessage();
    }
}

class SpecPart {
    String nodeDef;
    char typeTag;
    boolean globalUnique;
    StringBuilder globalPrefix;
    boolean localUnique;
    boolean mostRecent;
    boolean foreignKey;
    StringBuilder mostRecentIndex;
    boolean regex;
    StringBuilder regexPattern;
    String nextDelim;
    boolean verbose=false;
    
    public SpecPart() {
        this.nodeDef = "";
        this.nextDelim = "";
        this.verbose = false;
    }
    
    public SpecPart(SpecPart other) {
        this.nodeDef = other.nodeDef;
        this.typeTag = other.typeTag;
        this.globalUnique = other.globalUnique;
        if (other.globalPrefix!=null) {
            this.globalPrefix = new StringBuilder(other.globalPrefix.toString());
        }
        this.localUnique = other.localUnique;
        this.mostRecent = other.mostRecent;
        this.foreignKey = other.foreignKey;
        if (other.mostRecentIndex!=null) {
            this.mostRecentIndex = new StringBuilder(other.mostRecentIndex.toString());
        } else {
            this.mostRecentIndex = new StringBuilder();
        }
        this.regex = other.regex;
        if (other.regexPattern!=null) {
            this.regexPattern = new StringBuilder(other.regexPattern.toString());
        } else {
            this.regexPattern = new StringBuilder();
        }
        this.nextDelim = other.nextDelim;
    }
    
    @Override
    public String toString() {
        if (this.verbose) return verboseString();
        else return shortString();
    }
    
    public String shortString() {
        if (this.regex) return this.nodeDef + "?" + this.typeTag + "R{" + this.regexPattern.toString() + "}";
        else return this.nodeDef + "?" + this.typeTag;
    }
        
    public String verboseString() {
        StringBuilder out = new StringBuilder().append("{\n")
                .append("  nodeDef: ").append(this.nodeDef).append("\n")
                .append("  typeTag: ").append(this.typeTag).append("\n")
                .append("  globalUnique: ").append(this.globalUnique).append("\n")
                .append("  globalPrefix: ").append(this.globalPrefix).append("\n")
                .append("  localUnique: ").append(this.localUnique).append("\n")
                .append("  mostRecent: ").append(this.mostRecent).append("\n")
                .append("  mostRecentIndex: ").append(this.mostRecentIndex).append("\n")
                .append("  regex: ").append(this.regex).append("\n")
                .append("  regexPattern: ").append(this.regexPattern).append("\n")
                .append("  nextDelim: ").append(this.nextDelim).append("\n")
                .append("}");
        
        return out.toString();
    }
}

class SpecListItem {
    public Model model;
    public List<SpecPart> spec;
    
    public SpecListItem() {
        this.model = ModelFactory.createDefaultModel();
    }
    
    @Override
    public String toString() {
        return this.spec.toString();
    }
}

public class NodeSetUtil {
    public static EasyLogger print = new EasyLogger();
    static {
        print.filterLevel = 0;
    }
    private static String prefix = "tag:dirToRDF@null.net,2023:";
    public static String specPrefix = "example:";
    public static String dirname = "x";
        
        
    static Set<Character> DELIMS = Set.of('_', '.', ',', '(', ')', '/', '*');
    static Set<Character> TYPETAGS = Set.of('C', 'I', 'P', 'N');
    static Set<Character> SPECIAL = Set.of('?', '+', '{', '}', '@', '#');
    
    static boolean ACCEPTEDCHAR(char c) {
        return !(DELIMS.contains(c) || SPECIAL.contains(c));
    }
    
    
    public static Model fromDirectoryString(String dir, String specBlock, HashMap<String, Model> implied) throws Exception {
        List<SpecListItem> specList = new ArrayList();
        SpecListItem specList_i;
        List<SpecPart> spec;
        List<SpecPart> prepend = new ArrayList();

        boolean isBroken = false;

        for (String specString: specBlock.split("\n")) {
            specList_i = new SpecListItem();
            
            // Check if @ in specString, split specString and update model if true
            long atN = specString.chars().filter(c -> c=='@').count();
            if (atN>=1) {
                String[] split = specString.split("@");
                specString = split[0];
                
                for (int atCount=1; atCount<=atN; atCount++){
                    String modelKey = split[atCount].strip();
                    specList_i.model = specList_i.model.union(implied.get(modelKey));
                }
            }
            
            try {
                if (specString.charAt(0)=='!') {
                    prepend = buildSpec(specString.substring(1), new ArrayList());
                } else {
                    specList_i.spec = buildSpec(specString, prepend);
                    specList.add(specList_i);
                }
            } catch (SpecificationException e) {
                System.out.println(
                        "INFO: "+ String.format("Invalid specification string: %s. ", specString) + e.getMessage()
                );
                isBroken = true;
            }
        }
        
        print.debug(specList.toString());
        print.status("Done Creating Specs");

        if (isBroken) throw new Exception("Invalid specification file ends all processing!");

        Model pathModel;
        List<Model> pathModelList;
        Model dirModel = ModelFactory.createDefaultModel();
        
        long nodeSetCount = 1L;
        long dirCount = 0;
        
        for (String path: dir.split("\n")) {
            
            print.info(String.format("Processing path: %s", path));
            pathModelList = new ArrayList();
            
            List<String> matchedSpecs = new ArrayList();
            
            for (int i=0; i<specList.size(); i++) {
                specList_i = specList.get(i);
                spec = specList_i.spec;
                try {
                    pathModel = processPathGivenSpec(path, spec, nodeSetCount, specList_i.model);
                    pathModelList.add(pathModel);
                    matchedSpecs.add(spec.toString());
                } catch (ProcessingException e) {
                    print.info("Path DID NOT MATCH spec. " + e.getMessage());
                }
            }
            
            if (pathModelList.size()==0) {
                print.info(String.format("NO spec matched path: %s", path));
            } else if (pathModelList.size() > 1) {
                print.info(String.format("MULTIPLE specs matched path: %s", path));
                print.debug(matchedSpecs.toString());
            } else {
                dirModel = dirModel.union(pathModelList.get(0));
                nodeSetCount++;
            }
            
            
            dirCount++;
            if((dirCount)%500==0) {
                print.status("Number of files processed: "+String.valueOf(dirCount));
            }
        }
        
        return dirModel;
        
    }
    
    
    
    
    static List buildSpec(String specString, List<SpecPart> prefix) throws SpecificationException {
        List<SpecPart> spec = new ArrayList();
        
        for (SpecPart p: prefix) {
            spec.add(new SpecPart(p));
        }
        
        int state;
        SpecPart part;
        
        if (spec.isEmpty()) {
            part = new SpecPart();
            spec.add(part);
            state = 0;
        } else {
            part = spec.get(spec.size()-1);
            part.nextDelim = "";
            state = 2;
        }
        
        int n = specString.length();
        char c;
        StringBuilder tagParam = new StringBuilder();
        String complete = "";
        int bracketCount=0;
        
        for (int i=0; i<n; i++) {
            c = specString.charAt(i);
            complete += c;
            
            // skip to closing } char
            if (state==-2 && c=='{') {
                bracketCount++;
                if (bracketCount>1) {
                    tagParam.append(c);
                }
            } else if (state==-2 && c!='}') {
                tagParam.append(c);
            } else if (state==-2 && c=='}') {
                bracketCount--;
                
                if (bracketCount==0) {
                    state=2;
                    if (i==n-1) {
                        state=100;
                        part.nextDelim = null;
                    }
                } else {
                    tagParam.append(c);
                }
            }
            // skip to next " char
            else if (state==-1 && c!='"') {
                part.nextDelim += c;
            } else if (state==-1 && c=='"') {
                part = new SpecPart();
                spec.add(part);
                state=0;
            } 
            // Get the nodeDef and look for ?
            else if (state==0 && c=='"') {
                state = -1;
            } else if (state==0 && NodeSetUtil.ACCEPTEDCHAR(c)) {
                part.nodeDef += c;
            } else if (state==0 && c=='?') {
                state=1;
            } else if (state==0 && part.nodeDef.equals("") && NodeSetUtil.DELIMS.contains(c)) {
                part.nextDelim += c;
                part = new SpecPart();
                spec.add(part);
                state=0;
            } else if (state==0 && part.nodeDef.equals("") && c=='"') {
                state=-1;
            } else if (state==0) {
                throw new SpecificationException(c, complete, state, spec) ;
            } 
            // Add the type tag
            else if (state==1 && NodeSetUtil.TYPETAGS.contains(c)) {
                part.typeTag = c;
                state=2;
                if (i==n-1) {
                    part.nextDelim = null;
                    state=100;
                }
            } else if (state==1) {
                throw new SpecificationException(c, complete, state, spec) ;
            } 
            // Look for a delim or switch to adding otherTag state
            else if (state==2 && c=='+') {
                state=3;
            } else if (state==2 && NodeSetUtil.DELIMS.contains(c)) {
                part.nextDelim += c;
                part = new SpecPart();
                spec.add(part);
                state=0;
            } else if (state==2 && c=='"') {
                state=-1;
            } else if (state==2 && i==n-1) {
                part.nextDelim = null;
                state=100;
            } else if (state==2) {
                throw new SpecificationException(c, complete, state, spec) ;
            }
            // Add other tag and cycle back to state 2
            else if (state==3 && c=='G') {
                part.globalUnique = true;
                if (i<n-1 && specString.charAt(i+1)=='{') {
                    part.globalPrefix = new StringBuilder();
                    tagParam = part.globalPrefix;
                    state = -2;
                } else if (i==n-1) {
                    part.nextDelim = null;
                    part.globalPrefix = new StringBuilder();
                    state=100;
                } else {
                    part.globalPrefix = new StringBuilder();
                    state = 2;
                }
            } else if (state==3 && c=='L') {
                part.localUnique = true;
                state=2;
                if (i==n-1) {
                    part.nextDelim = null;
                    state=100;
                }
            } else if (state==3 && c=='F') {
                if (part.typeTag != 'P') throw new SpecificationException(c, complete, state, spec);
                part.foreignKey = true;
                state=2;
                if (i==n-1) {
                    part.nextDelim = null;
                    state=100;
                }
            } else if (state==3 && c=='M') {
                if (part.typeTag != 'P') throw new SpecificationException(c, complete, state, spec);
                part.mostRecent = true;
                if (i<n-1 && specString.charAt(i+1)=='{') {
                    part.mostRecentIndex = new StringBuilder();
                    tagParam = part.mostRecentIndex;
                    state = -2;
                } else if (i==n-1) {
                    part.nextDelim = null;
                    part.mostRecentIndex = new StringBuilder(0);
                    state=100;
                } else {
                    part.mostRecentIndex = new StringBuilder(0);
                    state = 2;
                }
            } else if (state==3 && c=='R') {
                part.regex = true;
                if (i==n-1) throw new SpecificationException(c, complete, state, spec) ;
                
                part.regexPattern = new StringBuilder();
                tagParam = part.regexPattern;
                state = -2;
            }
                

        }
        
        if (state != 100) {
            throw new SpecificationException(specString.charAt(specString.length()-1), complete, state, spec);
        }
        
        return spec;
    }
    
    
    
    
    static Model processPathGivenSpec(String path, List<SpecPart> spec, long nodeSetIndex, Model impliedModel) 
            throws ProcessingException {
        
        Model out = ModelFactory.createDefaultModel();
//        Resource File = out.createResource(prefix+"File");
//        out.add(File, RDF.type, RDFS.Class);
//        Resource x = out.createResource(specPrefix+path);
//        out.add(x, RDF.type, File);
        Resource NodeSetClass = impliedModel.createResource(prefix+"NodeSet");
        Resource nodeSet = impliedModel.createResource(prefix+"nodeSet"+String.valueOf(nodeSetIndex));
        impliedModel.add(nodeSet, RDF.type, NodeSetClass);
//        impliedModel.add(nodeSet, RDFS.label, "Path"+String.valueOf(nodeSetIndex));
        Property in = impliedModel.createProperty(prefix+"in");
        
        Pattern hasDelim = Pattern.compile("[_.,()\\/\\\\]");
        
        // Add everything from impliedModel to nodeSet
        
        impliedModel.listStatements().forEach(
                stmt -> {
                    out.add(stmt);
                    List<Resource> elements = new ArrayList();
                    
                    elements.add(stmt.getSubject());
                    elements.add(stmt.getPredicate());
                    if (stmt.getObject().canAs(Resource.class)) elements.add(stmt.getObject().as(Resource.class));
                    
                    for (Resource x: elements) {
                        if (!x.equals(NodeSetClass) && !x.equals(nodeSet) && !x.equals(in)) {
                            out.add(x, in, nodeSet);
                        }
                    }
                }
        );
        
//        out.add(x, in, nodeSet);
        Property source = out.createProperty(prefix+"source");
        Resource data = out.createResource(specPrefix+dirname+"/"+"Path"+String.valueOf(nodeSetIndex)+"-data");
        out.add(source, in, nodeSet);
        out.add(data, source, "file:///"+path);
        out.add(data, in, nodeSet);
        
        Resource Data = out.createResource(specPrefix+"Data");
        out.add(data, RDF.type, Data);
        out.add(Data, RDF.type, RDFS.Class);
        out.add(Data, in, nodeSet);
        
        List<Resource> instanceList = new ArrayList();
        
        String processedPath = "";
        String remainingPath = path;

        String IRI;
        String label;
        
        for (SpecPart s: spec) {
            print.debug(processedPath);
            print.debug(remainingPath);
            IRI = "";
            label = "";
            if (remainingPath.length()==0) {
                throw new ProcessingException("Path has less nodes than spec.");
            }

            if (s.globalUnique && s.localUnique) {
                throw new ProcessingException("Node cannot be local and global unique.");
            } else if (!s.globalUnique && !s.localUnique && s.typeTag=='I') {
                s.localUnique = true;
            } else if (!s.globalUnique && !s.localUnique) {
                // The default case if type is C or P. And it doesn't matter for N
                s.globalUnique = true;
            }
            
            print.debug(s.verboseString());
            
            if (s.localUnique) IRI += processedPath.replace("/", ".");
            else if (s.globalPrefix!=null) IRI += s.globalPrefix.toString();
            
            // Match regex
            if (s.regex) {
                String pattern = s.regexPattern.toString();
                if (pattern.charAt(0)!='^') pattern = "^" + pattern;
                Matcher m = Pattern.compile(pattern).matcher(remainingPath);
                
                try {
                    m.find();
                    String regexMatch = m.group();
                    String regexGroup = regexMatch;
                    if (s.regexPattern.indexOf("<x>")!=-1) regexGroup = m.group("x");
                    IRI += regexGroup;
                    label = regexGroup;
                    processedPath += regexMatch;
                    remainingPath = remainingPath.substring(regexMatch.length());
                } catch (java.lang.IllegalStateException e) {
                    print.warn(e.getMessage());
                    throw new ProcessingException("Issue with regex: " + pattern);
                }
                
                if (s.nextDelim!=null) {
                    String nd = s.nextDelim;
                    
                    if (remainingPath.length()==0) {
                        throw new ProcessingException("Regex match must be followed by specified delimiter.");
                    }
                    
                    if (nd.equals("*")) {
                        nd = remainingPath.substring(0,1);
                    }
                    
                    if (!remainingPath.startsWith(nd)) {
                        throw new ProcessingException("Regex match must be followed by specified delimiter.");
                    }

                    processedPath += nd;
                    remainingPath = remainingPath.substring(nd.length());
                }
            }
            // Match to end of string
            else if (s.nextDelim==null) {
                if (hasDelim.matcher(remainingPath).find()) {
                    throw new ProcessingException("Remaining String Contained Delim");
                }
                IRI += remainingPath;
                label = remainingPath;
                processedPath += remainingPath;
                remainingPath = "";
            }
            // Match up to next delim
            else {
                String matchToNext;
                
                String nd = s.nextDelim;
                
                if (nd.equals("*")) {
                    nd = remainingPath.substring(0,1);
                }
                
                int loc = remainingPath.indexOf(nd);
                            
                if (loc == -1) {
                    throw new ProcessingException(String.format("Next delimiter, %s, not found.", nd));
                }
                
                matchToNext = remainingPath.substring(0,loc);
                
                print.debug(matchToNext);
                
                // Not sure what these next two if statements are doing... useless? Detrimental?
                if (hasDelim.matcher(matchToNext).find()) {
                    throw new ProcessingException("Remaining String Contained Delim");
                }
                
                if (Pattern.matches("[\\.,\\(\\)_]", matchToNext)) {
                    Pattern p = Pattern.compile("[\\.,\\(\\)_]");
                    Matcher m = p.matcher(matchToNext);
                    m.find();
                    throw new ProcessingException(
                            String.format("Expected next delim %s. Found %s", nd, m.group())
                    );
                }
                
                remainingPath = remainingPath.substring(loc+nd.length());
                processedPath = processedPath + matchToNext + nd;
                IRI += matchToNext;
                label = matchToNext;
            }
            
            String pathIRI = opinionatedEncode(specPrefix+IRI);
            String specIRI = opinionatedEncode(specPrefix+s.nodeDef);
            if (s.globalPrefix!=null) label = s.globalPrefix.toString() + label;
//            pathIRI = specPrefix+IRI;
//            specIRI = specPrefix+s.nodeDef;
            
            
            if (s.typeTag=='C') {
                Resource superClass = out.createResource(specIRI);
                Resource newClass = out.createResource(pathIRI);
                
                out.add(newClass, RDFS.label, label);
                out.add(superClass, RDFS.label, s.nodeDef);
                out.add(newClass, RDFS.subClassOf, superClass);
                
                // NodeSet addition
                out.add(superClass, in, nodeSet);
                out.add(newClass, in, nodeSet);
            } 
            else if (s.typeTag=='I') {
                Resource typeClass = out.createResource(specIRI);
                Resource instance = out.createResource(pathIRI);
                
                out.add(instance, RDFS.label, IRI);
                out.add(typeClass, RDFS.label, s.nodeDef);
                out.add(typeClass, RDF.type, RDFS.Class);
                out.add(instance, RDF.type, typeClass);
                
                instanceList.add(instance);
                
                // NodeSet addition
                out.add(typeClass, in, nodeSet);
                out.add(instance, in, nodeSet);
            } 
            else if (s.typeTag=='P') {
                Property property = out.createProperty(specIRI);
                
                out.add(property, RDF.type, RDF.Property);
                out.add(property, RDFS.label, s.nodeDef);
                
                // NodeSet addition
                out.add(property, in, nodeSet);
                
                Resource instance = null;
                
                if (s.mostRecent) {
                    int i = (s.mostRecentIndex==null) ? Integer.parseInt(s.mostRecentIndex.toString()) : 0;
                    instance = instanceList.get(instanceList.size()-1-i);
                }
                
                if (s.foreignKey) {
                    Resource foreign = out.createResource(pathIRI);
                    
                    if (instance!=null) {
                        out.add(instance, property, foreign);
                        out.add(foreign, RDFS.label, label);
                    } else {
                        out.add(nodeSet, property, foreign);
                        out.add(foreign, RDFS.label, label);
                    }
                    
                    // NodeSet addition
                    out.add(foreign, in, nodeSet);
                } else {
                    Literal value = out.createLiteral(label);
                    
                    if (instance!=null) out.add(instance, property, value);
                    else out.add(nodeSet, property, value);
                }
            }
            
        }
        
        if (remainingPath.length()>0) throw new ProcessingException("Spec has less nodes than path.");
        
        return out;
    }
    
    
    public static String opinionatedEncode(String s) {
        String encoded = s.replace(" ", "-");
        encoded = encoded.replace("!", "%21");
        encoded = encoded.replace("#", "%23");
        encoded = encoded.replace("$", "%24");
        encoded = encoded.replace("%", "%25");
        encoded = encoded.replace("&", "%26");
        encoded = encoded.replace("'", "%27");
        encoded = encoded.replace("(", "_");
        encoded = encoded.replace(")", "_");
        encoded = encoded.replace("*", "%2A");
        encoded = encoded.replace("+", "%2B");
        encoded = encoded.replace(";", "%3B");
        encoded = encoded.replace("[", "_");
        encoded = encoded.replace("]", "_");
        return encoded;
    }
    
}
